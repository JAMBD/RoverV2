package com.rover.threadioio;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
public class OverRide extends Fragment {

    private TextView test;
    private int count = 0;

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
            if(test != null)
                test.setText(String.format("%d", count));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.drive, container, false);
        test = (TextView) rootView.findViewById(R.id.speed1);
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while(true){
                            getActivity().runOnUiThread(new ScreenUpdate());
                            try {
                                Thread.sleep(1000);
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