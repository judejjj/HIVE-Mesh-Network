package com.example.hive;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BroadcastActivity extends AppCompatActivity {

    private static final String TAG = "BroadcastActivity";
    private static final String SERVICE_ID = "com.example.hive";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private EditText etMessage;
    private View btnSend;
    private TextView tvLog;
    private ScrollView scrollLog;

    // Nearby Connections
    private ConnectionsClient connectionsClient;
    private String myNickname;
    private final List<String> connectedEndpoints = new ArrayList<>();

    // TASK 1: MAP IDs TO NICKNAMES
    private final Map<String, String> nicknameMap = new HashMap<>();

    // SOS STATE
    private boolean isActiveSOS = false;
    private String currentSosPayload = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null)
            getSupportActionBar().hide();
        setContentView(R.layout.activity_broadcast);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSendBroadcast);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        // Load Identity
        // Load Identity
        SharedPreferences prefs = getSharedPreferences("HivePrefs", Context.MODE_PRIVATE);
        String rawName = prefs.getString("KEY_NICKNAME", "Survivor");
        String uuid = prefs.getString("KEY_MY_UUID", UUID.randomUUID().toString());
        // Ensure UUID is saved if it was just generated
        if (!prefs.contains("KEY_MY_UUID")) {
            prefs.edit().putString("KEY_MY_UUID", uuid).apply();
        }

        // DISCORD TAG STRATEGY
        String suffix = uuid.substring(0, 4).toUpperCase();
        myNickname = rawName + "#" + suffix; // e.g. "Jude#A1B2"

        connectionsClient = Nearby.getConnectionsClient(this);

        btnSend.setOnClickListener(v -> sendManualMessage());

        appendLog("Initializing Radio as: " + myNickname);

        // CHECK INTENT FOR SOS
        checkSosIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkSosIntent(intent);
    }

    private void checkSosIntent(Intent intent) {
        if (intent != null && intent.hasExtra("SOS_MSG")) {
            String msg = intent.getStringExtra("SOS_MSG");
            if (msg != null && !msg.isEmpty()) {
                isActiveSOS = true;
                currentSosPayload = msg;
                appendLog(msg, true); // True for SOS UI
                
                // Immediately send to all currently connected peers
                if (!connectedEndpoints.isEmpty()) {
                    Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
                    connectionsClient.sendPayload(connectedEndpoints, payload);
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startAdvertising();
        startDiscovery();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectionsClient != null) {
            connectionsClient.stopAdvertising();
            connectionsClient.stopDiscovery();
            connectionsClient.stopAllEndpoints();
            connectedEndpoints.clear();
            MeshStateManager.connectedPeers.clear();
            nicknameMap.clear();
            appendLog("Radio Stopped.");
        }
    }

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(v -> appendLog("Advertising Active."))
                .addOnFailureListener(e -> appendLog("Adv Error: " + e.getMessage()));
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(v -> appendLog("Scanning for Uplinks..."))
                .addOnFailureListener(e -> appendLog("Scan Error: " + e.getMessage()));
    }

    private void sendManualMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg))
            return;

        if (connectedEndpoints.isEmpty()) {
            Toast.makeText(this, "No connected peers", Toast.LENGTH_SHORT).show();
            return;
        }

        Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(connectedEndpoints, payload);

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        // Show MY nickname? Or just "YOU"? User requested clean chat.
        // Usually "YOU" is better for self.
        appendLog("[" + time + "] " + myNickname + ": " + msg);
        etMessage.setText("");
    }

    // --- CALLBACKS ---

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            appendLog("Found Peer: " + info.getEndpointName());
            // Auto Connect
            connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener(e -> Log.e(TAG, "Connect Fail", e));
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            appendLog("Lost Peer: " + endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            appendLog("Connection Initiated: " + info.getEndpointName());

            // CAPTURE NAME
            nicknameMap.put(endpointId, info.getEndpointName());

            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                connectedEndpoints.add(endpointId);

                String name = nicknameMap.get(endpointId);
                if (name == null)
                    name = "Unknown";

                // Add to static state for PeerListActivity
                MeshStateManager.connectedPeers.add(new MeshStateManager.Peer(endpointId, name));

                appendLog(">>> CONNECTED: " + name);

                // LATE JOINER SOS SYNC
                if (isActiveSOS && !currentSosPayload.isEmpty()) {
                    Payload payload = Payload.fromBytes(currentSosPayload.getBytes(StandardCharsets.UTF_8));
                    List<String> targetList = new ArrayList<>();
                    targetList.add(endpointId);
                    connectionsClient.sendPayload(targetList, payload);
                }
            } else {
                appendLog("Connection Failed");
                nicknameMap.remove(endpointId);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            appendLog("Disconnected: " + endpointId);
            connectedEndpoints.remove(endpointId);
            nicknameMap.remove(endpointId);

            // REMOVE FROM STATIC STATE
            for (int i = 0; i < MeshStateManager.connectedPeers.size(); i++) {
                if (MeshStateManager.connectedPeers.get(i).id.equals(endpointId)) {
                    MeshStateManager.connectedPeers.remove(i);
                    break;
                }
            }
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);

                if (msg.startsWith("SOS:")) {
                    // LATE JOINER SOS CAPTURE & DEMULTIPLEX
                    SharedPreferences prefs = getSharedPreferences("HiveAlerts", Context.MODE_PRIVATE);
                    String currentList = prefs.getString("SOS_LIST", "");
                    if (!currentList.contains(msg)) {
                        String newList = msg + "|||" + currentList;
                        prefs.edit().putString("SOS_LIST", newList).apply();
                    }
                    appendLog(msg, true);
                } else {
                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

                    // LOOK UP NAME
                    String name = nicknameMap.get(endpointId);
                    if (name == null)
                        name = endpointId.substring(0, 4); // Fallback

                    appendLog("[" + time + "] " + name + ": " + msg);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
        }
    };

    private void appendLog(String text, boolean isSos) {
        runOnUiThread(() -> {
            if (isSos) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    tvLog.append(android.text.Html.fromHtml("<font color='#FF4444'><b>🚨 EMERGENCY BROADCAST:</b><br/>" + text + "</font><br><br>", android.text.Html.FROM_HTML_MODE_LEGACY));
                } else {
                    tvLog.append(android.text.Html.fromHtml("<font color='#FF4444'><b>🚨 EMERGENCY BROADCAST:</b><br/>" + text + "</font><br><br>"));
                }
            } else if (text.contains("🛡️_GOV_HQ_") || text.contains("Initializing Radio as: 🛡️_GOV_HQ_")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    tvLog.append(android.text.Html.fromHtml("<font color='#FFD700'>" + text + "</font><br>", android.text.Html.FROM_HTML_MODE_LEGACY));
                } else {
                    tvLog.append(android.text.Html.fromHtml("<font color='#FFD700'>" + text + "</font><br>"));
                }
            } else {
                tvLog.append(text + "\n");
            }
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void appendLog(String text) {
        appendLog(text, false);
    }
}