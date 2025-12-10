package com.example.hive;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class SosActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private EditText etDetails;
    private TextView tvCoordinates;
    private Button btnBroadcast, btnAutoDetect;
    private LatLng selectedLocation = new LatLng(28.6139, 77.2090); // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_sos);

        etDetails = findViewById(R.id.etSosDetails);
        tvCoordinates = findViewById(R.id.tvCoordinates);
        btnBroadcast = findViewById(R.id.btnBroadcastSos);
        btnAutoDetect = findViewById(R.id.btnAutoDetect);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        btnBroadcast.setOnClickListener(v -> sendSos());

        // AUTO DETECT: Just centers map on a "Simulated" GPS location for now
        // (Or real one if blue dot is available)
        btnAutoDetect.setOnClickListener(v -> {
            if (mMap != null) {
                // Simulate getting current GPS
                LatLng myPos = new LatLng(28.6139, 77.2090); // Replace with real GPS logic if needed
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, 16));
                placeMarker(myPos);
                Toast.makeText(this, "Location Acquired", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        updateCoordText(selectedLocation);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMapClickListener(this::placeMarker);
    }

    private void placeMarker(LatLng latLng) {
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng).title("Emergency Location"));
        selectedLocation = latLng;
        updateCoordText(latLng);
    }

    private void updateCoordText(LatLng loc) {
        tvCoordinates.setText(String.format("LOC: %.4f, %.4f", loc.latitude, loc.longitude));
    }

    private void sendSos() {
        String details = etDetails.getText().toString().trim();
        if (details.isEmpty()) { etDetails.setError("Required"); return; }

        String locStr = String.format("%.4f, %.4f", selectedLocation.latitude, selectedLocation.longitude);
        String msg = "SOS: " + details + " [AT: " + locStr + "]";

        Intent intent = new Intent(SosActivity.this, BroadcastActivity.class);
        intent.putExtra("SOS_MSG", msg);
        startActivity(intent);
        finish();
    }
}