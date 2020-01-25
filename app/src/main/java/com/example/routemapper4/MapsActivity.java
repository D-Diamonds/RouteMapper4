package com.example.routemapper4;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

/*
    The MapsActivity is the core of this app. It tracks the users current location fitting the screen to where they are and any markers on the map in the best way possible.
    *THIS DOES NOT MEAN ALL MARKERS WILL ALWAYS BE PRESENT IN VIEW*
    When the user wants to make a "turn" they can click the turn button to place a labeled turn marker and a line between it and the previous marker. This action also
    increases the current distance.
    When the user wants to "undo" a turn they can click the undo button which removes the newest marker, polyline, and distance addition.
    When the user wants to "end" their route they can click the end button to place an end marker, the final line, and the final distance. This action also disables
    the turn/end buttons and changes the undo button to a restart button.
    When the "restart" button is clicked the activity is ended and the app reverts to the MainActivity.
    Finally the app displaces the route distance after each marker change. If the distance is small enough it is in feet, else miles
    *NOTE THE START/END MARKERS CAN ONLY BE CHANGED BY RESTARTING THE ROUTE*
 */

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    // UI variables
    private GoogleMap mGoogleMap;
    private TextView distanceTxt;
    private Button multiButton;
    private Button turnButton;
    private Button endButton;

    // mapping variables
    private SupportMapFragment mapFrag;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private ArrayList<LatLng> previousLocations = new ArrayList<>();
    private ArrayList<MarkerOptions> markedLocations = new ArrayList<>();
    private LatLng currentlatLng;
    private boolean markerRequested = false;
    private int markerCount = 0;
    private double distance = 0;
    private FusedLocationProviderClient mFusedLocationClient;

    // constants
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

    // updates the distance text view with correct distance
    public void updateDistanceText() {
        if (distance < .8)
            distanceTxt.setText("Distance: " + Double.parseDouble(String.format("%.2f", distance * 5280)) + " Feet");
        else
            distanceTxt.setText("Distance: " + Double.parseDouble(String.format("%.2f", distance)) + " Miles");
    }

    // adds a new marker to the map
    public void addMarker(LatLng latLng, String title, float color) {
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title(title);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(color));
        mGoogleMap.addMarker(markerOptions);
        markedLocations.add(markerOptions);
        previousLocations.add(latLng);
    }

    // updates the camera to fit as many "markers" in the view as possible
    public void updateCamera() {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (MarkerOptions marker : markedLocations) {
            builder.include(marker.getPosition());
        }
        if (currentlatLng != null)
            builder.include(currentlatLng);
        LatLngBounds bounds = builder.build();
        int padding = 20;
        mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    // returns back to start activity
    public void endActivity() {
        Intent mainActivity = new Intent(this, MainActivity.class);
        startActivity(mainActivity);
        finish();
    }

    // starts locating
    public void startLocating() {
        // requesting location changes if location permissions is enabled
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        distanceTxt = findViewById(R.id.distanceTxt);
        multiButton = findViewById(R.id.multiButton);
        turnButton = findViewById(R.id.turnButton);
        endButton = findViewById(R.id.endButton);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(this);

        // turn button onclick
        turnButton.setOnClickListener(new View.OnClickListener() {
            // requests a new marker
            @Override
            public void onClick(View view) {
                markerRequested = true;
            }
        });

        // undo button onclick
        multiButton.setTag("undo");
        multiButton.setEnabled(false);
        multiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // if in undo mode - removes last placed marker, multline, and distance
                if (view.getTag().toString().equals("undo")) {
                    if (markedLocations.size() > 1) {
                        Log.i("Undo Button", "Undo");
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
                        updateCamera();
                    }
                }
                // if in restart mode - returns to main activity
                else if (view.getTag().toString().equals("restart")) {
                    Log.i("Undo Button", "Restart");
                    endActivity();
                }
            }
        });

        // end button onclick
        endButton.setOnClickListener(new View.OnClickListener() {
            // changes button texts, enabledness, and requests an end marker
            @Override
            public void onClick(View v) {
                multiButton.setText("Restart");
                multiButton.setTag("restart");
                turnButton.setEnabled(false);
                endButton.setEnabled(false);
                multiButton.setEnabled(true);
                markerRequested = true;
            }
        });

        // updates current location implementations
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                List<Location> locationList = locationResult.getLocations();
                if (locationList.size() > 0) {
                    //The last location in the list is the newest
                    Location currentLocation = locationList.get(locationList.size() - 1);
                    currentlatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                    Log.i("MapsActivity", "currentLocation: " + currentLocation.getLatitude() + " " + currentLocation.getLongitude());

                    // determines/sets if undo button is useable
                    if (markedLocations.size() == 1 )
                        multiButton.setEnabled(false);
                    else
                        multiButton.setEnabled(true);

                    // Place start marker, update distance text to default
                    if (markedLocations.size() == 0) {
                        addMarker(currentlatLng, "Start", BitmapDescriptorFactory.HUE_GREEN);
                        distanceTxt.setText("Distance: " + 0);
                    }
                    // Place current turn marker, draw polyline, update distance text
                    if (turnButton.isEnabled() && markerRequested && markedLocations.size() > 0) {
                        markerCount++;
                        LatLng previousLocation = previousLocations.get(previousLocations.size() - 1);
                        mGoogleMap.addPolyline(new PolylineOptions()
                                .clickable(false)
                                .add(previousLocation, currentlatLng)).setColor(POLYLINE_COLOR);
                        addMarker(currentlatLng, "Turn " + markerCount, BitmapDescriptorFactory.HUE_CYAN);
                        distance += getDistance(previousLocation.latitude, previousLocation.longitude, currentlatLng.latitude, currentlatLng.longitude);
                        updateDistanceText();
                        markerRequested = false;
                    }
                    // Place end marker, draw polyline, update distance text, end location callbacks
                    else if (!turnButton.isEnabled() && markerRequested && markedLocations.size() > 0) {
                        LatLng previousLocation = previousLocations.get(previousLocations.size() - 1);
                        mGoogleMap.addPolyline(new PolylineOptions()
                                .clickable(false)
                                .add(previousLocation, currentlatLng)).setColor(POLYLINE_COLOR);
                        addMarker(currentlatLng, "End", BitmapDescriptorFactory.HUE_RED);
                        distance += getDistance(previousLocation.latitude, previousLocation.longitude, currentlatLng.latitude, currentlatLng.longitude);
                        updateDistanceText();
                        markerRequested = false;
                        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
                        multiButton.setEnabled(true);
                    }
                    updateCamera();
                }
            }
        };
    }

    // required function
    @Override
    public void onPause() {
        super.onPause();

        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFusedLocationClient != null && mGoogleMap != null) {
            startLocating();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;
        mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        startLocating();
    }

    // Everything below here is obtaining user location permissions
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

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
        if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                    mGoogleMap.setMyLocationEnabled(true);
                }
            }
            else
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
        }
    }
}