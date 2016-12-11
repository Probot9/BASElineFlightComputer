package com.platypii.baseline.audible;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.platypii.baseline.Services;
import com.platypii.baseline.events.AudibleEvent;
import com.platypii.baseline.util.Util;
import org.greenrobot.eventbus.EventBus;

/**
 * Periodically gives audio feedback
 */
public class MyAudible {
    private static final String TAG = "Audible";

    private SharedPreferences prefs;

    private Speech speech;
    private AudibleThread audibleThread;

    private boolean isInitialized = false;
    private boolean isEnabled = false;

    // Was the last sample below/inside/above the boundary?
    private static final int STATE_MIN = -1;
    private static final int STATE_INSIDE = 0;
    private static final int STATE_MAX = 1;
    private int boundaryState = STATE_INSIDE;

    public void start(Context appContext) {
        Log.i(TAG, "Initializing audible");
        prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        speech = new Speech(appContext);

        if(!isInitialized) {
            isInitialized = true;
            audibleThread = new AudibleThread(this);

            AudibleSettings.load(prefs);
            isEnabled = prefs.getBoolean("audible_enabled", false);
            if(isEnabled) {
                enableAudible();
            }
        } else {
            Log.w(TAG, "Audible initialized twice");
            FirebaseCrash.report(new IllegalStateException("Audible initialized twice"));
        }
    }

    public void enableAudible() {
        Log.i(TAG, "Starting audible");
        if(isInitialized) {
            if(!audibleThread.isRunning()) {
                audibleThread.start();

                // Say audible mode
                speakModeWhenReady();

                // Play first measurement
                speakWhenReady();
            } else {
                Log.w(TAG, "Audible thread already started");
            }
        } else {
            Log.e(TAG, "Failed to start audible: audible not initialized");
        }
        isEnabled = true;
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("audible_enabled", true);
        editor.apply();
        EventBus.getDefault().post(new AudibleEvent());
    }

    public void disableAudible() {
        if(isInitialized) {
            speech.stopAll();
            audibleThread.stop();
            speech.speakWhenReady("Goodbye");
        } else {
            Log.e(TAG, "Failed to stop audible: audible not initialized");
        }
        isEnabled = false;
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("audible_enabled", false);
        editor.apply();
        EventBus.getDefault().post(new AudibleEvent());
    }

    /**
     * Announce the current audible mode
     */
    private void speakModeWhenReady() {
        speech.speakWhenReady(AudibleSettings.mode.name);
    }

    /**
     * Make a special announcement
     */
    public void speakNow(String text) {
        if(!isEnabled) {
            Log.e(TAG, "Should never speak when audible is disabled");
            FirebaseCrash.report(new IllegalStateException("MyAudible.speakNow should never speak when audible is disabled"));
        }
        if(speech != null) {
            speech.speakNow(text);
        } else {
            Log.e(TAG, "speakNow called but speech is null");
        }
    }

    void speak() {
        final String measurement = getMeasurement();
        if(speech != null && !measurement.isEmpty()) {
            speech.speakNow(measurement);
        }
    }

    private void speakWhenReady() {
        final String measurement = getMeasurement();
        if(speech != null && !measurement.isEmpty()) {
            speech.speakWhenReady(measurement);
        }
    }

    /**
     * Returns the text of what to say for the current measurement mode
     */
    private @NonNull String getMeasurement() {
        final AudibleSample sample = AudibleSettings.mode.currentSample(AudibleSettings.precision);
        // Check for fresh signal (not applicable to vertical speed)
        if(AudibleSettings.mode.id.equals("vertical_speed") || goodGpsFix()) {
            // Check for real valued sample
            if (Util.isReal(sample.value)) {
                if(sample.value < AudibleSettings.min) {
                    if(boundaryState != STATE_MIN) {
                        boundaryState = STATE_MIN;
                        return "min";
                    } else {
                        Log.i(TAG, "Not speaking: min, mode = " + AudibleSettings.mode.id + " sample = " + sample);
                        return "";
                    }
                } else if(AudibleSettings.max < sample.value) {
                    if(boundaryState != STATE_MAX) {
                        boundaryState = STATE_MAX;
                        return "max";
                    } else {
                        Log.i(TAG, "Not speaking: max, mode = " + AudibleSettings.mode.id + " sample = " + sample);
                        return "";
                    }
                } else {
                    boundaryState = STATE_INSIDE;
                    return sample.phrase;
                }
            } else {
                Log.w(TAG, "Not speaking: no signal, mode = " + AudibleSettings.mode.id + " sample = " + sample);
                return "";
            }
        } else {
            Log.w(TAG, "Not speaking: stale signal, mode = " + AudibleSettings.mode.id + " sample = " + sample);
            return "";
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Return true if GPS signal is fresh
     */
    private boolean goodGpsFix() {
        if(Services.location.lastLoc != null && Services.location.lastFixDuration() < 3500) {
            gpsFix = true;
        } else {
            if(Services.location.lastLoc == null) {
                Log.w(TAG, "No GPS signal");
            } else {
                Log.w(TAG, "Stale GPS signal");
            }
            if(gpsFix) {
                speech.speakNow("Signal lost");
            }
            gpsFix = false;
        }
        return gpsFix;
    }
    /** True iff the last measurement was a good fix */
    private boolean gpsFix = false;

    /**
     * Stop audible service
     */
    public void stop() {
        if(isInitialized) {
            if(audibleThread.isRunning()) {
                disableAudible();
            }
            audibleThread = null;
            isInitialized = false;
            speech = null;
        }
    }

}
