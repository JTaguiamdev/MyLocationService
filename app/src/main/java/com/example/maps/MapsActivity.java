package com.example.maps;


import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Priority;



import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.maps.R;
import com.example.maps.databinding.ActivityMapsBinding;


import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.location.LocationServices;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.maps.android.PolyUtil;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private final ArrayList<Marker> markedMarkers = new ArrayList<>();
    private boolean isMarkingEnabled = false;
    private Location lastKnownLocation = null;
    private Polyline currentPolyline = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.map_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_mark) {

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
                return true;
            }

            // Request a fresh GPS location each time
            fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
            ).addOnSuccessListener(location -> {

                if (location == null) {
                    Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                    return;
                }

                LatLng current = new LatLng(location.getLatitude(), location.getLongitude());

                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(current)
                        .title("Marked Location"));

                if (marker != null) {
                    markedMarkers.add(marker);
                }

                Toast.makeText(this, "Location marked.", Toast.LENGTH_SHORT).show();
            });

            return true;
        }


        if (item.getItemId() == R.id.action_view_path) {
            drawPath();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMapClickListener(latLng -> {
            if (isMarkingEnabled) {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Marked Location"));
                if (marker != null) {
                    markedMarkers.add(marker);
                }
            }
        });

        mMap.setOnMarkerClickListener(marker -> {
            if (isMarkingEnabled && markedMarkers.contains(marker)) {
                markedMarkers.remove(marker);
                marker.remove();
                return true;
            }
            return false;
        });

        enableMyLocation();
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            getDeviceLocation();
        } else {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void getDeviceLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                fusedLocationClient.getLastLocation()
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                lastKnownLocation = task.getResult();
                                if (lastKnownLocation != null) {
                                    LatLng pos = new LatLng(
                                            lastKnownLocation.getLatitude(),
                                            lastKnownLocation.getLongitude()
                                    );
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                                }
                            }
                        });
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void drawPath() {

        if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
        }

        if (lastKnownLocation == null) {
            Toast.makeText(this, "Current location unknown", Toast.LENGTH_SHORT).show();
            getDeviceLocation();
            return;
        }

        if (markedMarkers.isEmpty()) {
            Toast.makeText(this, "No locations marked", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build a sequence: current location → all marked points (in order)
        ArrayList<LatLng> pathPoints = new ArrayList<>();

        // Add device location as starting point
        pathPoints.add(new LatLng(
                lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude()
        ));

        // Add markers in the order they were marked
        for (Marker marker : markedMarkers) {
            pathPoints.add(marker.getPosition());
        }

        // Draw a polyline connecting all points
        currentPolyline = mMap.addPolyline(
                new PolylineOptions()
                        .addAll(pathPoints)
                        .color(Color.BLUE)
                        .width(10f)
        );

        Toast.makeText(this, "Connected all marked points in order.", Toast.LENGTH_SHORT).show();
    }


    private String getDirectionsUrl(LatLng origin, LatLng dest, ArrayList<LatLng> waypoints) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDest = "destination=" + dest.latitude + "," + dest.longitude;
        String sensor = "sensor=false";
        String mode = "mode=walking";

        StringBuilder params = new StringBuilder(strOrigin + "&" + strDest + "&" + sensor + "&" + mode);

        if (!waypoints.isEmpty()) {
            params.append("&waypoints=optimize:true|");
            for (int i = 0; i < waypoints.size(); i++) {
                LatLng p = waypoints.get(i);
                params.append(p.latitude).append(",").append(p.longitude);
                if (i < waypoints.size() - 1) {
                    params.append("|");
                }
            }
        }

        String key = getApiKey();
        params.append("&key=").append(key);

        return "https://maps.googleapis.com/maps/api/directions/json?" + params;
    }

    private String getApiKey() {
        try {
            ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);

            return ai.metaData.getString("com.google.android.geo.API_KEY", "");
        } catch (Exception e) {
            return "";
        }
    }

    private String downloadUrl(String urlStr) throws Exception {
        StringBuilder sb = new StringBuilder();
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;

        try {
            URL url = new URL(urlStr);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();

            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            br.close();
        } finally {
            if (iStream != null) iStream.close();
            if (urlConnection != null) urlConnection.disconnect();
        }

        return sb.toString();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            }
        }
    }

}
