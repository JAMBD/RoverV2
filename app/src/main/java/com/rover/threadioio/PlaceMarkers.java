package com.rover.threadioio;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class PlaceMarkers extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private ArrayList<Marker> markers;
    Polyline path;
    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorManager;
    private Sensor mMag;
    private Sensor mAcc;
    private Location last_loc;
    private float mBearing;
    private float desiredBearing;
    float[] mGravity;
    float[] mGeomagnetic;

    // UDP communication
    private MyDatagramReceiver myDatagramReceiver = null;
    private String m_ip_address = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.maptab);
        setUpMapIfNeeded();
        Log.d("NAV", "OnCreate");

        // Connect to the google api client for maps api
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

        // Setup the sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mMag, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mAcc, SensorManager.SENSOR_DELAY_NORMAL);

        markers = new ArrayList<>();
        mMap.setMyLocationEnabled(true);
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                MarkerOptions mo = new MarkerOptions()
                        .position(latLng)
                        .title("Click to delete way point")
                        .draggable(true);
                Marker m = mMap.addMarker(mo);
                markers.add(m);
                draw_robot_path();
                Log.d("Nav", "Add marker");
            }
        });
        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                markers.remove(marker);
                marker.remove();
                draw_robot_path();
            }
        });
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {

            }

            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                draw_robot_path();
            }
        });


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("IP Address");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_ip_address = input.getText().toString();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    protected void onPause() {
        myDatagramReceiver.kill();
        super.onPause();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    private void draw_robot_path() {
        if (path != null) path.remove();
        PolylineOptions p = new PolylineOptions();
        for (Marker m : markers) {
            p.add(m.getPosition());
        }
        path = mMap.addPolyline(p);
        //  Need to get a bearing to follow
        // get the current target
        if (!markers.isEmpty() && last_loc != null) {
            Location target = new Location("");
            target.setLongitude(markers.get(0).getPosition().longitude);
            target.setLatitude(markers.get(0).getPosition().latitude);
            desiredBearing = last_loc.bearingTo(target);
            Log.d("NAV", "Updated bearing");
        } else {
            desiredBearing = -1000;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        myDatagramReceiver = new MyDatagramReceiver();
        myDatagramReceiver.start();
    }

    /**
     * Sets up the maptab if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the maptab has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the maptab.
        if (mMap == null) {
            // Try to obtain the maptab from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the maptab.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        Log.d("NAV", "Setup Map");
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d("Nav", "Connected");
        last_loc = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (last_loc != null) {
            LatLng latLng = new LatLng(last_loc.getLatitude(), last_loc.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
            mMap.animateCamera(cameraUpdate);
        }

        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Nav", "Connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("Nav", "Connection failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("Nav", "Got location!!!");
        last_loc = location;
        if (markers.isEmpty()) return;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        // Where I am has been updated
        // Get my current target
        Location target = new Location("");
        while (true) {
            target.setLatitude(markers.get(0).getPosition().latitude);
            target.setLongitude(markers.get(0).getPosition().longitude);
            // Am I within 5m? if yes then remove target and get a new one
            double dist = location.distanceTo(target);
            if (dist > 5) break;

            // Reached target!
            markers.remove(0);
            if (markers.isEmpty()) {
                // We're done
                break;
            }
        }
        draw_robot_path();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values.clone();
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values.clone();
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity,
                    mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                mBearing = (float) (orientation[0] * (180 / Math.PI));
                //Log.d("NAV", "Bearing is " + mBearing);
                if (last_loc != null) {
                    if (desiredBearing != -1000) {
                        float difBearing = mBearing - desiredBearing;
                        Log.d("NAV", "Diff " + difBearing + " target " + desiredBearing);
                    }
                    // Rotate the maptab - mainly to demonstate this works, probably annoying for use
                    CameraPosition currentPlace = new CameraPosition.Builder()
                            .target(new LatLng(last_loc.getLatitude(), last_loc.getLongitude()))
                            .bearing(mBearing)
                            .zoom(18f)
                            .build();
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(currentPlace));


                    new Thread(new Runnable() {
                        public void run(){
                            DatagramSocket s = null;
                            InetAddress local = null;
                            String messageStr = "Lat: " + last_loc.getLatitude() + " Long: " +
                                    last_loc.getLongitude() + " Bearing: " + mBearing;
                            int server_port = 9002;
                            try {
                                s = new DatagramSocket();
                                if (s == null) {
                                    Log.d("NAV", "Socket fail");
                                    return;
                                }
                                if (m_ip_address == null) {
                                    local = InetAddress.getByName("192.168.1.6");
                                } else {
                                    local = InetAddress.getByName(m_ip_address);
                                }
                                int msg_length=messageStr.length();
                                byte[] message = messageStr.getBytes();
                                DatagramPacket p = new DatagramPacket(message, msg_length,local,server_port);
                                s.send(p);
                            } catch (SocketException e) {
                                e.printStackTrace();
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    // This receives a message if needed. Kept as an example, not currently used.
    private class MyDatagramReceiver extends Thread {
        private boolean bKeepRunning = true;
        private String lastMessage = "";

        public void run() {
            String message;
            byte[] lmessage = new byte[100];
            DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

            try {
                DatagramSocket socket = new DatagramSocket(9001);

                while (bKeepRunning) {
                    socket.receive(packet);
                    message = new String(lmessage, 0, packet.getLength());
                    lastMessage = message;
                    runOnUiThread(updateTextMessage);
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        public void kill() {
            bKeepRunning = false;
        }

        public String getLastMessage() {
            return lastMessage;
        }

        private Runnable updateTextMessage = new Runnable() {
            public void run() {
                if (myDatagramReceiver == null) return;
                Log.d("NAV", myDatagramReceiver.getLastMessage());
            }
        };
    }
};