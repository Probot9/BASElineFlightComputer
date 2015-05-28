package com.platypii.baseline.data;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;

public class TheCloud {

    private static final String baselineServer = "https://base-line.ws";
    private static final String postUrl = baselineServer + "/tracks";

    public static void uploadAsync(final Jump jump) {
        new AsyncTask<Void,Void,CloudData>() {
            @Override
            protected CloudData doInBackground(Void... voids) {
                // Upload to the cloud
                return TheCloud.upload(jump);
            }
//            @Override
//            protected void onPostExecute(String url) {
//                 final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//                 context.startActivity(browserIntent);
//            }
        }.execute();
    }

    /** Upload to the cloud */
    public static CloudData upload(Jump jump) {
        // Check if track is already uploaded
        final CloudData cloudData = jump.getCloudData();
        if(cloudData != null) {
            // Already uploaded, return url
            return cloudData;
        } else if(isNetworkConnected()) {
            // Upload to the cloud
            try {
                // Save cloud data
                final CloudData result = postJump(jump);
                jump.setCloudData(result);
                Log.i("Jump", "Upload successful, url " + result.trackUrl);
                return result;
            } catch(IOException e) {
                Log.e("Cloud", "Failed to upload file", e);
                return null;
            } catch(JSONException e) {
                Log.e("Cloud", "Failed to parse response", e);
                return null;
            }
        } else {
            Log.w("Cloud", "Network not connected");
            return null;
        }
    }

    private static CloudData postJump(Jump jump) throws IOException, JSONException {
        final File file = jump.logFile;
        final URL url = new URL(postUrl);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        final long contentLength = file.length();
        try {
            conn.setDoOutput(true);
            // Write to OutputStream
            if(contentLength > Integer.MAX_VALUE) {
                conn.setChunkedStreamingMode(0);
            } else {
                conn.setFixedLengthStreamingMode((int) contentLength);
            }
            final OutputStream os = new BufferedOutputStream(conn.getOutputStream());
            copy(new FileInputStream(file), os);
            // Read response
            final int status = conn.getResponseCode();
            if(status == 200) {
                // Read body
                final String body = toString(conn.getInputStream());
                return CloudData.fromJson(body);
            } else {
                throw new IOException("http status code " + status);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        final byte buffer[] = new byte[1024];
        int bytesRead;
        while((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
    }

    private static String toString(InputStream input) throws IOException {
        final StringBuilder builder = new StringBuilder();
        byte buffer[] = new byte[1024];
        int bytesRead;
        while((bytesRead = input.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, bytesRead));
        }
        return builder.toString();
    }

    private static boolean isNetworkConnected() {
//        final ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//        final NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
//        return (networkInfo != null && networkInfo.isConnected());
        return true; // TODO
    }

}