package com.rover.threadioio;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.app.Fragment;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;

/**
 * Created by freelance on 31/01/15.
 */
public class Waypoint extends Fragment implements OnTargetReachedListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private ArrayList<Marker> markers;
    Polyline path;
    private static View rootView;
    private IOIOThread ioio;

    public Waypoint(){
        mMap = null;
    }

    public void setIOIO(IOIOThread ioio){
        this.ioio = ioio;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if(rootView != null){
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if(parent != null)
                parent.removeView(rootView);
        }
        try {
            rootView = inflater.inflate(R.layout.maptab, container, false);
        }catch(InflateException e){

        }
        setUpMapIfNeeded();
        return rootView;
    }


    /**
     * Sets up the maptab if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the maptab has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link com.google.android.gms.maps.SupportMapFragment} (and
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
            mMap = ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the maptab.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        ioio.set_at_target_cb(this);
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
    }

    private void draw_robot_path() {
        if (path != null) path.remove();
        PolylineOptions p = new PolylineOptions();
        for (Marker m : markers) {
            p.add(m.getPosition());
        }
        path = mMap.addPolyline(p);

        if (!markers.isEmpty()) {
            Location target = new Location("");
            target.setLongitude(markers.get(0).getPosition().longitude);
            target.setLatitude(markers.get(0).getPosition().latitude);
            ioio.set_target(target);
        } else {
            ioio.set_target(null);
        }
    }

    @Override
    public void OnFinished() {
        Log.d("WP", "Reached target");
        Marker m = markers.remove(0);
        m.remove();
        draw_robot_path();
    }
}

