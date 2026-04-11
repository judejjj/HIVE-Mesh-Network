package com.example.hive;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AlertsActivity extends AppCompatActivity {

    private RecyclerView rvAlerts;
    private AlertAdapter alertAdapter;
    private List<String> alertList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_alerts);

        rvAlerts = findViewById(R.id.rvAlerts);
        rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        alertAdapter = new AlertAdapter(alertList);
        rvAlerts.setAdapter(alertAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAlerts();
    }

    private void loadAlerts() {
        SharedPreferences alertPrefs = getSharedPreferences("HiveAlerts", Context.MODE_PRIVATE);
        String alerts = alertPrefs.getString("SOS_LIST", "");
        
        alertList.clear();
        if (!alerts.isEmpty()) {
            String[] split = alerts.split("\\|\\|\\|");
            for (String s : split) {
                if (!s.trim().isEmpty()) {
                    alertList.add(s.trim());
                }
            }
        }
        
        if (alertList.isEmpty()) {
            alertList.add("No active alerts in sector.");
        }
        alertAdapter.notifyDataSetChanged();
    }

    class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.ViewHolder> {
        private List<String> data;

        AlertAdapter(List<String> data) { this.data = data; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String msg = data.get(position);
            holder.text.setText(msg);
            holder.text.setTextColor(0xFFFF5252); // Red

            holder.itemView.setOnClickListener(v -> {
                if (!msg.equals("No active alerts in sector.")) {
                    Intent intent = new Intent(AlertsActivity.this, ViewAlertActivity.class);
                    // Extract details
                    String details = msg;
                    String coords = "Unknown";
                    if (msg.contains("[AT:")) {
                        int index = msg.indexOf("[AT:");
                        details = msg.substring(0, index).replace("SOS: ", "").trim();
                        coords = msg.substring(index).replace("[AT:", "").replace("]", "").trim();
                    } else {
                        details = msg.replace("SOS: ", "").trim();
                    }
                    intent.putExtra("ALERT_DETAILS", details);
                    intent.putExtra("ALERT_COORDS", coords);
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }
}