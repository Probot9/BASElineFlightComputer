package com.platypii.baseline.augmented;

import com.platypii.baseline.cloud.BaselineCloud;
import com.platypii.baseline.util.Callback;
import com.platypii.baseline.util.IOUtil;
import com.platypii.baseline.util.Try;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import com.google.firebase.crash.FirebaseCrash;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetch geo data for a given track
 */
class GeoDataTask extends AsyncTask<Void,Void,Try<List<Location>>> {
    private static final String TAG = "GeoDataTask";

    private final String track_id;
    private final Callback<List<Location>> cb;

    GeoDataTask(String track_id, Callback<List<Location>> cb) {
        this.track_id = track_id;
        this.cb = cb;
    }

    @Override
    protected Try<List<Location>> doInBackground(Void... voids) {
        Log.i(TAG, "Fetching geo data for track " + track_id);
        try {
            // Make HTTP request
            final List<Location> result = fetchGeoData(track_id);
            Log.i(TAG, "Fetch geo data successful: " + result.size());
            return new Try.Success<>(result);
        } catch(IOException e) {
            Log.e(TAG, "Failed to fetch geo data", e);
            FirebaseCrash.report(e);
            return new Try.Failure<>(e.getMessage());
        } catch(JSONException e) {
            Log.e(TAG, "Failed to parse response", e);
            FirebaseCrash.report(e);
            return new Try.Failure<>(e.toString());
        }
    }

    @Override
    protected void onPostExecute(Try<List<Location>> result) {
        if(cb != null) {
            if(result instanceof Try.Success) {
                final List<Location> trackData = ((Try.Success<List<Location>>) result).result;
                cb.apply(trackData);
            } else {
                final String error = ((Try.Failure<List<Location>>) result).error;
                cb.error(error);
            }
        }
    }

    private static String geoDataUrl(String track_id) {
        return BaselineCloud.baselineServer + "/v1/tracks/" + track_id + "/geodata";
    }

    /**
     * Make http request to BASEline server for track listing
     */
    private static List<Location> fetchGeoData(String track_id) throws IOException, JSONException {
        final URL url = new URL(geoDataUrl(track_id));
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // conn.setRequestProperty("Authorization", auth);
        try {
            // Read response
            final int status = conn.getResponseCode();
            if(status == 200) {
                // Read body
                final String body = IOUtil.toString(conn.getInputStream());
                return parseListing(body);
            } else if(status == 401) {
                throw new IOException("authorization required");
            } else {
                throw new IOException("http status code " + status);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Parse raw json into a list of geo data
     */
    private static List<Location> parseListing(String jsonString) throws JSONException {
        final JSONObject json = new JSONObject(jsonString);
        final JSONArray jsonArray = json.getJSONArray("data");

        final ArrayList<Location> points = new ArrayList<>();
        for(int i = 0; i < jsonArray.length(); i++) {
            final JSONObject jsonObject = jsonArray.getJSONObject(i);
            final Location point = parseLocation(jsonObject);
            points.add(point);
        }
        return points;
    }

    private static Location parseLocation(JSONObject json) throws JSONException {
        final Location loc = new Location("l");
        loc.setLatitude(json.getDouble("lat"));
        loc.setLongitude(json.getDouble("lon"));
        loc.setAltitude(json.getDouble("alt"));
        return loc;
    }

}
