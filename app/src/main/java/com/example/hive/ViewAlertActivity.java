package com.example.hive;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import android.preference.PreferenceManager;
import android.content.Context;

public class ViewAlertActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_view_alert);

        TextView tvDetails = findViewById(R.id.tvDetails);
        TextView tvCoordinates = findViewById(R.id.tvCoordinates);

        String details = getIntent().getStringExtra("ALERT_DETAILS");
        String coords = getIntent().getStringExtra("ALERT_COORDS");

        if (details != null) tvDetails.setText(details);
        if (coords != null) tvCoordinates.setText(coords);

        // Parse coords and set map
        if (coords != null && !coords.equals("Unknown")) {
            try {
                String[] parts = coords.split(",");
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());

                GeoPoint threatLocation = new GeoPoint(lat, lon);
                MapView mMap = findViewById(R.id.offlineMap);
                mMap.setMultiTouchControls(true);
                mMap.getController().setZoom(17.0);
                mMap.getController().setCenter(threatLocation);

                Marker marker = new Marker(mMap);
                marker.setPosition(threatLocation);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setTitle("Threat Origin");
                mMap.getOverlays().add(marker);
            } catch (Exception e) {
                // Formatting error, ignore for map
            }
        }
    }
}
