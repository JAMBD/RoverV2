package com.rover.threadioio;

import android.util.Log;

import java.io.IOException;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PID;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;

/**
 * Created by freelance on 31/01/15.
 */
public class IOIOThread implements IOIOLooperProvider {

    @Override
    public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
        Log.i("IOIOservice", "IOIO setup");
        return createIOIOLooper();

    }

    class Looper extends BaseIOIOLooper {
        /** The on-board LED. */
        private DigitalOutput led_;
        private PID pid1_;
        private PID pid2_;
        private PID pid3_;
        private PID pid4_;

        /**
         * Called every time a connection with IOIO has been established.
         * Typically used to open pins.
         *
         * @throws ioio.lib.api.exception.ConnectionLostException
         *             When IOIO connection is lost.
         *
         */
        @Override
        protected void setup() throws ConnectionLostException {
            led_ = ioio_.openDigitalOutput(0, true);
            pid1_ = ioio_.openPID(1);
            pid2_ = ioio_.openPID(2);
            pid3_ = ioio_.openPID(3);
            pid4_ = ioio_.openPID(4);
            try {
                pid1_.setParam(1,0,0);
                pid2_.setParam(1,0,0);
                pid3_.setParam(1,0,0);
                pid4_.setParam(1,0,0);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.i("IOIOservice", "IOIO setup");
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
            led_.write(false);
            try {
                float driveVel = 0;
                float driveAng = 0;

                float left = driveVel * (float)Math.cos((double)driveAng) + driveVel * (float)Math.sin((double)driveAng);
                float right = -driveVel * (float)Math.cos((double)driveAng) + driveVel * (float)Math.sin((double)driveAng);
                pid1_.setSpeed(left);
                pid2_.setSpeed(right);
                pid3_.setSpeed(-left);
                pid4_.setSpeed(-right);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Thread.sleep(10);
        }

        /**
         * Called when the IOIO is disconnected.
         *
         * @see ioio.lib.util.IOIOLooper#disconnected()
         */
        @Override
        public void disconnected() {
            Log.i("IOIOservice", "IOIO bye bye");

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