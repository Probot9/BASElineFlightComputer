package com.platypii.baseline.tracks;

import com.platypii.baseline.BaseService;
import com.platypii.baseline.Services;
import com.platypii.baseline.events.LoggingEvent;
import com.platypii.baseline.location.MyLocationListener;
import com.platypii.baseline.measurements.MLocation;
import com.platypii.baseline.measurements.MPressure;
import com.platypii.baseline.measurements.Measurement;
import com.platypii.baseline.sensors.MySensorListener;
import com.platypii.baseline.util.Exceptions;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Logs location, altitude, every scrap of data we can get to a file.
 * AKA- The Black Box flight recorder
 */
public class TrackLogger implements MyLocationListener, MySensorListener, BaseService {
    private static final String TAG = "TrackLogger";

    private boolean logging = false;

    private long startTimeMillis = System.currentTimeMillis();
    private long startTimeNano = System.nanoTime();
    private long stopTimeNano = -1;

    // Log file
    private File logDir;
    private TrackFile trackFile;
    private BufferedWriter log;

    public void start(@NonNull final Context context) {
        AsyncTask.execute(() -> logDir = TrackFiles.getTrackDirectory(context));
    }

    public synchronized void startLogging() {
        if(!logging && logDir != null) {
            Log.i(TAG, "Starting logging");
            logging = true;
            startTimeMillis = System.currentTimeMillis();
            startTimeNano = System.nanoTime();
            stopTimeNano = -1;
            try {
                // Pick a log file
                trackFile = newTrackFile();

                // Update state before first byte is written
                // Otherwise user can browse to it, and uploader might upload it
                Services.trackState.setState(trackFile, TrackState.RECORDING);

                // Start recording
                startFileLogging(trackFile.file);

                // Notify listeners that recording has started
                EventBus.getDefault().post(new LoggingEvent(true, null));
            } catch(IOException e) {
                Log.e(TAG, "Error starting logging", e);
            }
        } else if(logDir == null) {
            Log.e(TAG, "startLogging() called before start()");
        } else {
            Log.e(TAG, "startLogging() called when database already logging");
        }
    }

    /**
     * Stop data logging, and return track data
     */
    public synchronized void stopLogging() {
        if(logging) {
            logging = false;
            Log.i(TAG, "Stopping logging");
            final TrackFile trackFile = stopFileLogging();
            if(trackFile != null) {
                // Update state before notifying listeners (such as upload manager)
                Services.trackState.setState(trackFile, TrackState.NOT_UPLOADED);
                EventBus.getDefault().post(new LoggingEvent(false, trackFile));
            } else {
                Exceptions.report(new IllegalStateException("Result of stopFileLogging should not be null"));
            }
        } else {
            Log.e(TAG, "stopLogging() called when database isn't logging");
        }
    }

    public boolean isLogging() {
        return logging;
    }

    public long getStartTime() {
        if(logging) {
            return startTimeMillis;
        } else {
            return 0;
        }
    }

    /**
     * Returns the amount of time we've been logging, as a nice string 0:00.000
     */
    public String getLogTime() {
        if(logging) {
            long nanoTime;
            if (stopTimeNano == -1) {
                nanoTime = System.nanoTime() - startTimeNano;
            } else {
                nanoTime = stopTimeNano - startTimeNano;
            }
            final long millis = (nanoTime / 1000000L) % 1000;
            final long seconds = (nanoTime / 1000000000L) % 60;
            final long minutes = nanoTime / 60000000000L;
            return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis);
        } else {
            return "";
        }
    }

    private TrackFile newTrackFile() {
        // Name file based on current timestamp
        final SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        final String timestamp = dt.format(new Date());

        // gzipped CSV log file
        File file = new File(logDir, "track_" + timestamp + ".csv.gz");
        // Avoid filename conflicts
        for(int i = 2; file.exists(); i++) {
            file = new File(logDir, "track_" + timestamp + "_" + i + ".csv.gz");
        }

        return new TrackFile(file);
    }

    private void startFileLogging(@NonNull File logFile) throws IOException {
        // Open track file for writing
        log = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(logFile))));

        // Write header
        log.write(Measurement.header + "\n");

        // Start sensor updates
        EventBus.getDefault().register(this);
        Services.location.addListener(this);
        Services.sensors.addListener(this);

        Log.i(TAG, "Logging to " + logFile);
    }

    /**
     * Stop logging and return the track file.
     * Precondition: logging = true
     */
    private TrackFile stopFileLogging() {
        stopTimeNano = System.nanoTime();

        // Stop sensor updates
        EventBus.getDefault().unregister(this);
        Services.location.removeListener(this);
        Services.sensors.removeListener(this);

        // Close file writer
        try {
            log.close();
            Log.i(TAG, "Logging stopped for " + trackFile);
            return trackFile;
        } catch (IOException e) {
            Log.e(TAG, "Failed to close log file " + trackFile, e);
            Exceptions.report(e);
            return null;
        }
    }

    /**
     * Listen for altitude updates
     */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onAltitudeEvent(@NonNull MPressure alt) {
        if(!Double.isNaN(alt.pressure)) {
            logLine(alt.toRow());
        }
    }

    /**
     * Listen for location updates
     */
    @Override
    public void onLocationChanged(@NonNull MLocation measure) {
        if(!Double.isNaN(measure.latitude) && !Double.isNaN(measure.longitude)) {
            logLine(measure.toRow());
        }
    }

    /**
     * Listen for sensor updates
     */
    @Override
    public void onSensorChanged(@NonNull Measurement measure) {
        logLine(measure.toRow());
    }

    /**
     * Write a measurement to the track file
     * @param line the measurement to store
     */
    private synchronized void logLine(@NonNull String line) {
        if(logging) {
            try {
                log.write(line);
                log.write('\n');
            } catch (IOException e) {
                Log.e(TAG, "Failed to write to track file " + trackFile, e);
                Exceptions.report(e);
            }
        } else {
            Exceptions.report(new IllegalStateException("Attempted to log after closing file"));
        }
    }

    @Override
    public void stop() {
        if(logging) {
            Log.e(TAG, "TrackLogger.stop() called, but still logging");
        }
    }

}
