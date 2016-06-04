package com.platypii.baseline.altimeter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import java.util.ArrayList;

import com.platypii.baseline.Services;
import com.platypii.baseline.util.Stat;
import com.platypii.baseline.data.measurements.MAltitude;
import com.platypii.baseline.data.measurements.MLocation;
import com.platypii.baseline.location.MyLocationListener;

/**
 * Altimeter manager
 * TODO: Get corrections via barometer, GPS, DEM
 * TODO: Model acceleration
 */
public class MyAltimeter {
    private static final String TAG = "MyAltimeter";

    private static SensorManager sensorManager;

    // Listeners
    private static final ArrayList<MyAltitudeListener> listeners = new ArrayList<>();

    // Pressure data
    public static float pressure = Float.NaN; // hPa (millibars)
    public static double pressure_altitude_raw = Double.NaN; // pressure converted to altitude under standard conditions (unfiltered)
    public static double pressure_altitude_filtered = Double.NaN; // kalman filtered pressure altitude
    // public static double altitude_raw = Double.NaN; // pressure altitude adjusted for altitude offset (unfiltered)

    // official altitude = pressure_altitude - altitude_offset
    // altitude_offset uses GPS to get absolute altitude right
    public static double altitude_offset = 0.0;

    // Data filter
    private static final Filter filter = new FilterKalman(); // Unfiltered(), AlphaBeta(), MovingAverage(), etc

    // Official altitude data
    public static double altitude = Double.NaN; // Meters AMSL
    public static double climb = Double.NaN; // Rate of climb m/s
    // public static double verticalAcceleration = Double.NaN;

    // Ground level
    public static double ground_level = Double.NaN;

    private static long lastFixNano; // nanoseconds
    private static long lastFixMillis; // milliseconds

    // Stats
    // Model error is the difference between our filtered output and the raw pressure altitude
    // Model error should approximate the sensor variance, even when in motion
    public static final Stat model_error = new Stat();
    private static long n = 0; // number of samples
    public static float refreshRate = 0; // Moving average of refresh rate in Hz

    /**
     * Initializes altimeter services, if not already running
     * @param appContext The Application context
     */
    public static synchronized void start(@NonNull Context appContext) {
        if(sensorManager == null) {
            // Add sensor listener
            sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
            final Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (sensor != null) {
                // Start sensor updates
                sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }

            // Start GPS updates
            if(Services.location != null) {
                Services.location.addListener(locationListener);
            } else {
                Log.e(TAG, "Location services should be initialized before altimeter");
            }
        } else {
            Log.w(TAG, "MyAltimeter already started");
        }
    }

    public static double altitudeAGL() {
        return pressure_altitude_filtered - ground_level;
    }

    // Sensor Event Listener
    private static final SensorEventListener sensorEventListener = new AltimeterSensorEventListener();
    private static class AltimeterSensorEventListener implements SensorEventListener {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        public void onSensorChanged(@NonNull SensorEvent event) {
            long millis = System.currentTimeMillis(); // Record time as soon as possible
            // assert event.sensor.getType() == Sensor.TYPE_PRESSURE;
            // Log.w(TAG, "values[] = " + event.values[0] + ", " + event.values[1] + ", " + event.values[2]);
            MyAltimeter.updateBarometer(millis, event);
        }
    }

    // Location Listener
    private static final MyLocationListener locationListener = new AltimeterLocationListener();
    private static class AltimeterLocationListener implements MyLocationListener {
        public void onLocationChanged(@NonNull MLocation loc) {
            MyAltimeter.updateGPS(loc);
        }
        public void onLocationChangedPostExecute() {}
    }

    /**
     * Process new barometer reading
     */
    private static void updateBarometer(long millis, SensorEvent event) {
        if(event == null || event.values.length == 0 || Double.isNaN(event.values[0]))
            return;

        double prevAltitude = altitude;
        // double prevClimb = climb;
        long prevLastFixNano = lastFixNano;

        pressure = event.values[0];
        lastFixNano = event.timestamp;
        lastFixMillis = millis - Services.location.phoneOffsetMillis; // Convert to GPS time

        // Barometer refresh rate
        final long deltaTime = lastFixNano - prevLastFixNano; // time since last refresh
        if(deltaTime > 0) {
            final float refreshTime = 1E9f / (float) (deltaTime);
            refreshRate += (refreshTime - refreshRate) * 0.5f; // Moving average
            if (Double.isNaN(refreshRate)) {
                Log.e(TAG, "Refresh rate is NaN, deltaTime = " + deltaTime + " refreshTime = " + refreshTime);
                refreshRate = 0;
            }
        }

        // Convert pressure to altitude
        pressure_altitude_raw = pressureToAltitude(pressure);

        // altitude_raw = pressure_altitude_raw - altitude_offset; // the current pressure converted to altitude AMSL. noisy.

        // Update the official altitude
        final double dt = Double.isNaN(prevAltitude)? 0 : (lastFixNano - prevLastFixNano) * 1E-9;
        // Log.d(TAG, "Raw Altitude AGL: " + Convert.distance(altitude_raw) + ", dt = " + dt);

        filter.update(pressure_altitude_raw, dt);

        pressure_altitude_filtered = filter.x;
        altitude = pressure_altitude_filtered - altitude_offset;
        climb = filter.v;

        // Compute model error
        model_error.addSample(pressure_altitude_filtered - pressure_altitude_raw);

        // Log.d("Altimeter", "alt = " + altitude + ", climb = " + climb);

        if(Double.isNaN(altitude)) {
            Log.w(TAG, "Altitude should not be NaN: altitude = " + altitude);
        }

        // Adjust for ground level
        if(n == 0) {
            // First pressure reading. Calibrate ground level.
            ground_level = pressure_altitude_raw;
        } else if(n < 16) {
            // Average the first N raw samples
            ground_level += (pressure_altitude_raw - ground_level) / (n + 1);
        }

        n++;
        updateAltitude();
    }

    private static long gps_sample_count = 0;
    /**
     * Process new GPS reading
     */
    private static void updateGPS(MLocation loc) {
        // Log.d(TAG, "GPS Update Time: " + System.currentTimeMillis() + " " + System.nanoTime() + " " + loc.millis);
        if(!Double.isNaN(loc.altitude_gps)) {
            if(n > 0) {
                // Log.d(TAG, "alt = " + altitude + ", alt_gps = " + altitude_gps + ", offset = " + altitude_offset);
                // GPS correction for altitude AMSL
                if(gps_sample_count == 0) {
                    // First altitude reading. Calibrate ground level.
                    altitude_offset = pressure_altitude_filtered - loc.altitude_gps;
                } else {
                    // Average the first N samples, then use moving average with lag 20
                    final double altitude_error = altitude - loc.altitude_gps;
                    final long correction_factor = Math.min(gps_sample_count, 20);
                    final double altitude_correction = altitude_error / correction_factor;
                    altitude_offset += altitude_correction;
                }
            } else {
                // No barometer use gps
                final double prevAltitude = altitude;
                final long prevLastFix = lastFixMillis;
                lastFixMillis = loc.millis;
                // Update the official altitude
                altitude = loc.altitude_gps;
                if(Double.isNaN(prevAltitude)) {
                    climb = 0;
                } else {
                    final double dt = (lastFixMillis - prevLastFix) * 1E-3;
                    climb = (altitude - prevAltitude) / dt; // m/s
                }
            }
            gps_sample_count++;
            updateAltitude();
        }
    }

    /**
     * Saves an official altitude measurement
     */
    private static void updateAltitude() {
        // Log.d(TAG, "Altimeter Update Time: " + System.currentTimeMillis() + " " + System.nanoTime() + " " + lastFixMillis + " " + lastFixNano);
        // Create the measurement
        final MAltitude myAltitude = new MAltitude(lastFixMillis, lastFixNano, altitude, climb, pressure);
        // Notify listeners (using AsyncTask so the altimeter never blocks!)
        new AsyncTask<MAltitude,Void,Void>() {
            @Override
            protected Void doInBackground(MAltitude... params) {
                synchronized(listeners) {
                    for(MyAltitudeListener listener : listeners) {
                        listener.altitudeDoInBackground(params[0]);
                    }
                }
                return null;
            }
            @Override
            protected void onPostExecute(Void result) {
                for(MyAltitudeListener listener : listeners) {
                    listener.altitudeOnPostExecute();
                }
            }
        }.execute(myAltitude);
    }

    // ISA pressure and temperature
    private static final double altitude0 = 0; // ISA height 0 meters
    private static final double pressure0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE; // ISA pressure 1013.25 hPa
    private static final double temp0 = 288.15; // ISA temperature 15 degrees celcius

    // Physical constants
//    private static final double G = 9.80665; // Gravity (m/s^2)
//    private static final double R = 8.31432; // Universal Gas Constant ((N m)/(mol K))
//    private static final double M = 0.0289644; // Molar Mass of air (kg/mol)
    private static final double L = -0.0065; // Temperature Lapse Rate (K/m)
    private static final double EXP = 0.190263237;// - L * R / (G * M);

    /**
     * Convert air pressure to altitude
     * @param pressure Pressure in hPa
     * @return The pressure altitude in meters
     */
    private static double pressureToAltitude(double pressure) {
        // Barometric formula
        return altitude0 - temp0 * (1 - Math.pow(pressure / pressure0, EXP)) / L;
    }

    /**
     * Add a new listener for us to notify
     */
    public static void addListener(MyAltitudeListener listener) {
        synchronized(listeners) {
            listeners.add(listener);
        }
    }
    public static void removeListener(MyAltitudeListener listener) {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }

    public static void stop() {
        Services.location.removeListener(locationListener);
        sensorManager.unregisterListener(sensorEventListener);
        sensorManager = null;

        if(listeners.size() > 0) {
            Log.e(TAG, "Stopping altimeter service, but listeners are still listening");
        }
    }

}