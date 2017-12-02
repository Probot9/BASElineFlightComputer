package com.platypii.baseline.cloud;

import com.platypii.baseline.BaseActivity;
import com.platypii.baseline.Services;
import com.platypii.baseline.events.AuthEvent;
import com.platypii.baseline.events.LoggingEvent;
import com.platypii.baseline.events.SyncEvent;
import com.platypii.baseline.tracks.TrackFile;
import com.platypii.baseline.tracks.TrackState;
import com.platypii.baseline.util.Exceptions;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Manages track uploads.
 * This includes queueing finished tracks, and retrying in the future.
 */
public class UploadManager {
    private static final String TAG = "UploadManager";

    // Mapping from local track file to cloud data
    private final Map<TrackFile,CloudData> completedUploads = new HashMap<>();

    // Upload queue
    private UploadQueue queue = new UploadQueue();

    private Context context;

    public void start(Context context) {
        this.context = context;
        // Listen for track completion
        EventBus.getDefault().register(this);
        // Check for queued tracks to upload
        queue.start(context);
    }

    private void uploadAll() {
        // Upload queued tracks
        queue.tendQueue(context);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onLoggingEvent(@NonNull LoggingEvent event) {
        if(BaseActivity.currentAuthState == AuthEvent.SIGNED_IN && !event.started) {
            Log.i(TAG, "Auto syncing track " + event.trackFile);
            Exceptions.log("Logging stopped, autosyncing track " + event.trackFile);
            uploadAll();
        }
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUploadSuccess(@NonNull SyncEvent.UploadSuccess event) {
        Services.trackState.setState(event.trackFile, TrackState.UPLOADED);
        completedUploads.put(event.trackFile, event.cloudData);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUploadFailure(@NonNull SyncEvent.UploadFailure event) {
        Services.trackState.setState(event.trackFile, TrackState.NOT_UPLOADED);
    }

    public CloudData getCompleted(TrackFile trackFile) {
        return completedUploads.get(trackFile);
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
    }

}
