package com.example.routemapper4;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mGoogleMap;
    private TextView distanceTxt;
    private SupportMapFragment mapFrag;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private ArrayList<LatLng> previousLocations = new ArrayList<>();
    private ArrayList<MarkerOptions> markedLocations = new ArrayList<>();
    private boolean markerRequested = false;
    private int markerCount = 0;
    private double distance = 0;
    private FusedLocationProviderClient mFusedLocationClient;

    private final int POLYLINE_COLOR = 0xffff00ff;

    // gets distance between user guess and correct location in miles
    public double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371e3; // metres
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double Δφ = Math.toRadians(lat2-lat1);
        double Δλ = Math.toRadians(lon2-lon1);

        double a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
                Math.cos(φ1) * Math.cos(φ2) *
                        Math.sin(Δλ/2) * Math.sin(Δλ/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return ((R * c) / 1000) / 1.609;
    }

    public void updateDistanceText() {
        if (distance < .8)
            distanceTxt.setText("Distance: " + Double.parseDouble(String.format("%.2f", distance * 5280)) + " Feet");
        else
            distanceTxt.setText("Distance: " + Double.parseDouble(String.format("%.2f", distance)) + " Miles");
    }

    public void addMarker(LatLng latLng, String title, float color) {
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title(title);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(color));
        mGoogleMap.addMarker(markerOptions);
        markedLocations.add(markerOptions);
        previousLocations.add(latLng);
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 20));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        distanceTxt = findViewById(R.id.distanceTxt);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(this);

        findViewById(R.id.turnButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                markerRequested = true;
            }
        });

        findViewById(R.id.undoButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (markedLocations.size() > 1) {
                    distance -= getDistance(previousLocations.get(previousLocations.size() - 2).latitude, previousLocations.get(previousLocations.size() - 2).longitude, previousLocations.get(previousLocations.size() - 1).latitude, previousLocations.get(previousLocations.size() - 1).longitude);
                    updateDistanceText();
                    markedLocations.remove(markedLocations.size() - 1);
                    previousLocations.remove(previousLocations.size() - 1);
                    markerCount--;
                    mGoogleMap.clear();
                    for (MarkerOptions markerOptions : markedLocations)
                        mGoogleMap.addMarker(markerOptions);
                    for (int i = 1; i < previousLocations.size(); i++)
                        mGoogleMap.addPolyline(new PolylineOptions()
                                .clickable(false)
                                .add(previousLocations.get(i - 1), previousLocations.get(i))).setColor(POLYLINE_COLOR);
                }
            }
        });

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                List<Location> locationList = locationResult.getLocations();
                if (locationList.size() > 0) {
                    //The last location in the list is the newest
                    Location location = locationList.get(locationList.size() - 1);
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    Log.i("MapsActivity", "Location: " + location.getLatitude() + " " + location.getLongitude());
                    if (markedLocations.size() == 0) {
                        addMarker(latLng, "Start Position", BitmapDescriptorFactory.HUE_GREEN);
                        distanceTxt.setText("Distance: " + 0);
                    }
                    //Place current location marker
                    if (markerRequested && markedLocations.size() > 0) {
                        markerCount++;
                        addMarker(latLng, "Turn " + markerCount, BitmapDescriptorFactory.HUE_CYAN);
                        LatLng previousLocation = previousLocations.get(previousLocations.size() - 1);
                        mGoogleMap.addPolyline(new PolylineOptions()
                                .clickable(false)
                                .add(previousLocation, latLng)).setColor(POLYLINE_COLOR);
                        distance += getDistance(previousLocation.latitude, previousLocation.longitude, latLng.latitude, latLng.longitude);
                        updateDistanceText();
                        markerRequested = false;
                    }
                }
            }
        };
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;
        mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10);
        mLocationRequest.setFastestInterval(1);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //Location Permission already granted
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                mGoogleMap.setMyLocationEnabled(true);
            } else {
                //Request Location Permission
                checkLocationPermission();
            }
        }
        else {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mGoogleMap.setMyLocationEnabled(true);
        }
    }

    // Everything below here is obtaining user location permissions
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("Location Permission Needed")
                        .setMessage("This app needs the Location permission, please accept to use location functionality")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MapsActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION );
                            }
                        })
                        .create()
                        .show();
            }
            else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mGoogleMap.setMyLocationEnabled(true);
                    }
                }
                else {
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }
}
