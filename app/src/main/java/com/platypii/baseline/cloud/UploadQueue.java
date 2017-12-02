package com.platypii.baseline.cloud;

import com.platypii.baseline.BaseActivity;
import com.platypii.baseline.Services;
import com.platypii.baseline.events.AuthEvent;
import com.platypii.baseline.tracks.TrackFile;
import com.platypii.baseline.tracks.TrackFiles;
import com.platypii.baseline.tracks.TrackState;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Upload queued tracks to the cloud
 */
class UploadQueue {
    private static final String TAG = "UploadQueue";

    // Queue tender thread
    private Runnable tenderRunnable;

    void start(@NonNull Context context) {
        // Initial tend queue
        tendQueue(context);
    }

    /**
     * Enqueue a track for upload
     */
    void tendQueue(@NonNull Context context) {
        if (tenderRunnable != null) {
            Log.i(TAG, "Skipping queue tending - tender already running");
        } else if (BaseActivity.currentAuthState != AuthEvent.SIGNED_IN) {
            Log.i(TAG, "Skipping queue tending - not signed in");
        } else {
            Log.i(TAG, "Starting queue tender");
            tenderRunnable = () -> {
                final boolean success = uploadQueue(context);
                if(!success) {
                    Log.w(TAG, "Error uploading at least one queued track");
                    // TODO: Try again later
                } else {
                    // Stop queue tender thread to save resources
                    tenderRunnable = null;
                }
            };
            // Start tender thread
            new Thread(tenderRunnable).start();
        }
    }

    /**
     * Iterate through tracks and upload queued tracks
     * @return true iff there were no failures
     */
    private boolean uploadQueue(@NonNull Context context) {
        int errors = 0;
        for(TrackFile trackFile : TrackFiles.getTracks(context)) {
            if(Services.trackState.getState(trackFile) == TrackState.QUEUED) {
                // Upload track
                final boolean success = UploadTask.upload(context, trackFile);
                if(!success) errors++;
            }
        }
        return errors == 0;
    }

}
