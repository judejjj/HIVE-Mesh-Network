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

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import android.preference.PreferenceManager;
import android.content.Context;

public class SosActivity extends AppCompatActivity {

    private MapView mMap;
    private EditText etDetails;
    private TextView tvCoordinates;
    private Button btnBroadcast, btnAutoDetect;
    private GeoPoint selectedLocation = new GeoPoint(28.6139, 77.2090); // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_sos);

        etDetails = findViewById(R.id.etSosDetails);
        tvCoordinates = findViewById(R.id.tvCoordinates);
        btnBroadcast = findViewById(R.id.btnBroadcastSos);
        btnAutoDetect = findViewById(R.id.btnAutoDetect);

        mMap = findViewById(R.id.offlineMap);
        mMap.setMultiTouchControls(true);
        mMap.getController().setZoom(16.0);
        mMap.getController().setCenter(selectedLocation);
        updateCoordText(selectedLocation);

        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                placeMarker(p);
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        mMap.getOverlays().add(new MapEventsOverlay(mReceive));

        btnBroadcast.setOnClickListener(v -> sendSos());

        // AUTO DETECT: Just centers map on a "Simulated" GPS location for now
        btnAutoDetect.setOnClickListener(v -> {
            if (mMap != null) {
                // Simulate getting current GPS
                GeoPoint myPos = new GeoPoint(28.6139, 77.2090); // Replace with real GPS logic if needed
                mMap.getController().animateTo(myPos);
                placeMarker(myPos);
                Toast.makeText(this, "Location Acquired", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void placeMarker(GeoPoint geoPoint) {
        mMap.getOverlays().removeIf(overlay -> overlay instanceof Marker); // Clear existing markers
        Marker marker = new Marker(mMap);
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("Emergency Location");
        mMap.getOverlays().add(marker);
        mMap.invalidate();
        
        selectedLocation = geoPoint;
        updateCoordText(geoPoint);
    }

    private void updateCoordText(GeoPoint loc) {
        tvCoordinates.setText(String.format("LOC: %.4f, %.4f", loc.getLatitude(), loc.getLongitude()));
    }

    private void sendSos() {
        String details = etDetails.getText().toString().trim();
        if (details.isEmpty()) { etDetails.setError("Required"); return; }

        String locStr = String.format("%.4f, %.4f", selectedLocation.getLatitude(), selectedLocation.getLongitude());
        String msg = "SOS: " + details + " [AT: " + locStr + "]";

        Intent intent = new Intent(SosActivity.this, BroadcastActivity.class);
        intent.putExtra("SOS_MSG", msg);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}