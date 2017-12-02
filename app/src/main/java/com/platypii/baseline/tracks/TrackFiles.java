package com.platypii.baseline.tracks;

import com.platypii.baseline.Services;
import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manage track files on the device.
 * Files are saved in either the external files dir, or the internal files.
 * Unsynced track files are stored in the top level directory.
 * Synced track files are moved to the "synced" directory.
 */
public class TrackFiles {
    private static final String TAG = "TrackFiles";

    @NonNull
    public static List<TrackFile> getTracks(@NonNull Context context) {
        final List<TrackFile> tracks = new ArrayList<>();
        // Load jumps from disk
        final File logDir = getTrackDirectory(context);
        if(logDir != null) {
            final File[] files = logDir.listFiles();
            for (File file : files) {
                final String filename = file.getName();
                final TrackFile trackFile = new TrackFile(file);
                // Tracks look like "track_(yyyy-MM-dd_HH-mm-ss).csv.gz"
                final boolean matchesFilenamePattern = filename.startsWith("track_") && filename.endsWith(".csv.gz");
                // Track is not actively logging
                final boolean isLogging = Services.trackState.getState(trackFile) == TrackState.RECORDING;
                if(matchesFilenamePattern && !isLogging) {
                    tracks.add(trackFile);
                }
            }
            // Sort by date descending
            Collections.sort(tracks, (track1, track2) -> -track1.getName().compareTo(track2.getName()));
            return tracks;
        } else {
            Log.e(TAG, "Track storage directory not available");
            return tracks;
        }
    }

    public static File getTrackDirectory(@NonNull Context context) {
        final String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return context.getExternalFilesDir(null);
        } else {
            Log.w(TAG, "External storage directory not available, falling back to internal storage");
            return context.getFilesDir();
        }
    }

}
