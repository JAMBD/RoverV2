package com.rover.threadioio;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.IOException;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PID;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;
import rover.nicta.joystick.JoystickView;

/**
 * Created by freelance on 31/01/15.
 */
public class OverRide extends Fragment {

    private ToggleButton button_;

    private TextView sp1_;
    private TextView sp2_;
    private TextView sp3_;
    private TextView sp4_;

    private JoystickView val_;

    private IOIOThread ioio;

    private int count = 0;

    public void setIOIO(IOIOThread ioio){
        this.ioio = ioio;
    }

    public OverRide (){
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while(true) {
                            count++;
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        ).start();
    }


    private class ScreenUpdate implements Runnable{

        @Override
        public void run() {
            if(button_ != null && ioio != null){
                ioio.set_led(!button_.isChecked());
                ioio.set_vel(val_.getVelocity());
                ioio.set_ang(val_.getAngle());

                sp1_.setText(String.format("%.4f", ioio.speed1));
                sp2_.setText(String.format("%.4f", ioio.speed2));
                sp3_.setText(String.format("%.4f", ioio.speed3));
                sp4_.setText(String.format("%.4f", ioio.speed4));
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.drive, container, false);

        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        button_ = (ToggleButton) rootView.findViewById(R.id.button);

        sp1_ = (TextView) rootView.findViewById(R.id.speed1);
        sp2_ = (TextView) rootView.findViewById(R.id.speed2);
        sp3_ = (TextView) rootView.findViewById(R.id.speed3);
        sp4_ = (TextView) rootView.findViewById(R.id.speed4);

        val_ = (JoystickView) rootView.findViewById(R.id.drive);
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while(true){
                            getActivity().runOnUiThread(new ScreenUpdate());
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        ).start();
        return rootView;
    }

}