package com.rover.threadioio;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PID;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;

/**
 * Created by freelance on 31/01/15.
 */
public class IOIOThread implements IOIOLooperProvider,GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener {

    // For sensors + GPS
    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorManager;
    private Sensor mMag;
    private Sensor mAcc;
    private Location last_loc;
    private float mBearing;
    private float desiredBearing;
    private long last_loc_time;
    float[] mGravity;
    float[] mGeomagnetic;

    public IOIOThread(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mMag, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mAcc, SensorManager.SENSOR_DELAY_NORMAL);
        // Connect to the google api client for maps api
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }


    @Override
    public IOIOLooper createIOIOLooper(String connectionType, Object extra) {

        return createIOIOLooper();
    }
    public boolean ledSTATE = true;
    public float velocity = 0;
    public float angle = 0;
    private Location target;
    private boolean is_at_target;
    private float turn_factor = -0.008f;
    private float target_dist_thresh = 2.0f;
    private boolean override;
    private OnTargetReachedListener onTargetReachedListener;

    public Float speed1;
    public Float speed2;
    public Float speed3;
    public Float speed4;

    public void set_led(boolean state){ if(override) ledSTATE = state;}
    public void set_vel(float vel){
        if(override) velocity = vel;
    }
    public void set_ang(float ang){
        if(override) angle = ang;
    }
    public void set_override(boolean allow_override) {
        override = allow_override;
    }

    public void set_target(Location loc) {
        target = loc;
        if (last_loc != null && target != null) {
            is_at_target = last_loc.distanceTo(target) < target_dist_thresh;
        }
    }

    public void set_at_target_cb(OnTargetReachedListener oprl) {
        onTargetReachedListener = oprl;
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Google API client is connected
        Log.d("IOIO", "Google API client connected");
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("IOIO", "Google API client connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("IOIO", "Google API client connection failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("IOIO", "Location changed");
        last_loc = location;
        last_loc_time = System.currentTimeMillis();
        if (target == null) return;
        desiredBearing = last_loc.bearingTo(target);
        if (last_loc.distanceTo(target) < target_dist_thresh) {
            is_at_target = true;
            Log.d("IOIO", "At target");
            onTargetReachedListener.OnFinished();
        } else {
            Log.d("IOIO", last_loc.distanceTo(target) + "m to target");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values.clone();
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values.clone();
        }
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity,
                    mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                mBearing = (float) (orientation[0] * (180 / Math.PI));
                // Don't set motors since device is being remote controlled
                if (override) return;
                if (last_loc == null || (System.currentTimeMillis() - last_loc_time > 5000) ||
                        target == null || is_at_target) {
                    // Lost or has no target so stop
                    velocity = 0;
                    angle = 0;
                    //Log.d("IOIO", "Set motors to stop");
                } else {
                    // Correct path
                    velocity = (0.15f); // always move at the same speed...
                    angle = ((mBearing - desiredBearing) * turn_factor);
                    Log.d("IOIO", "Bearing diff: " + ((mBearing - desiredBearing)));
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("IOIO", "Sensor changed accuracy");
    }

    class Looper extends BaseIOIOLooper {
        /** The on-board LED. */
        private DigitalOutput led_;
        private PwmOutput pwm1_;
        private PwmOutput pwm2_;
        private PwmOutput pwm3_;
        private PwmOutput pwm4_;
        private DigitalOutput dir1_;
        private DigitalOutput dir2_;
        private DigitalOutput dir3_;
        private DigitalOutput dir4_;


        /**
         * Called every time a connection with IOIO has been established.
         * Typically used to open pins.
         *
         * @throws ConnectionLostException
         *             When IOIO connection is lost.
         *
         */
        @Override
        protected void setup() throws ConnectionLostException{
            led_ = ioio_.openDigitalOutput(0, true);

            dir1_ = ioio_.openDigitalOutput(41, true);
            dir2_ = ioio_.openDigitalOutput(42, true);
            dir3_ = ioio_.openDigitalOutput(43, true);
            dir4_ = ioio_.openDigitalOutput(44, true);

            pwm1_ = ioio_.openPwmOutput(35,500);
            pwm2_ = ioio_.openPwmOutput(36,500);
            pwm3_ = ioio_.openPwmOutput(37,500);
            pwm4_ = ioio_.openPwmOutput(38,500);
            pwm1_.setDutyCycle(0);
            pwm2_.setDutyCycle(0);
            pwm3_.setDutyCycle(0);
            pwm4_.setDutyCycle(0);
        }

        /**
         * Called repetitively while the IOIO is connected.
         *
         * @throws ConnectionLostException
         *             When IOIO connection is lost.
         * @throws InterruptedException
         * 				When the IOIO thread has been interrupted.
         *
         * @see ioio.lib.util.IOIOLooper#loop()
         */
        @Override
        public void loop() throws ConnectionLostException, InterruptedException {
            led_.write(ledSTATE);
            float driveVel = velocity;
            float driveAng = angle;

            float left = driveVel * (float)Math.cos((double)driveAng) + driveVel * (float)Math.sin((double)driveAng);
            float right = driveVel * (float)Math.cos((double)driveAng) - driveVel * (float)Math.sin((double)driveAng);
            pwm1_.setDutyCycle(Math.abs(left));
            pwm2_.setDutyCycle(Math.abs(right));
            pwm3_.setDutyCycle(Math.abs(left));
            pwm4_.setDutyCycle(Math.abs(right));
            dir1_.write(left > 0);
            dir2_.write(right > 0);
            dir3_.write(left < 0);
            dir4_.write(right < 0);

            speed1 = -left;
            speed2 = -right;
            speed3 = -left;
            speed4 = -right;
            Thread.sleep(10);
        }

        /**
         * Called when the IOIO is disconnected.
         *
         * @see ioio.lib.util.IOIOLooper#disconnected()
         */
        @Override
        public void disconnected() {
        }

        /**
         * Called when the IOIO is connected, but has an incompatible firmware version.
         *
         * @see ioio.lib.util.IOIOLooper#incompatible(ioio.lib.api.IOIO)
         */
        @Override
        public void incompatible() {
        }
    }

    protected IOIOLooper createIOIOLooper() {
        return new Looper();
    }
}