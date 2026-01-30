package com.example.hive;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PeerListActivity extends AppCompatActivity {

    private static final String SERVICE_ID = "com.example.hive";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    // --- UI ELEMENTS ---
    private LinearLayout layoutScanner;
    private LinearLayout layoutChat;

    // Scanner UI
    private TextView tvPeerStatus, tvEmpty;
    private RecyclerView rvPeers;
    private Button btnScan;
    private PeerAdapter adapter;
    private final List<DiscoveredPeer> discoveredPeers = new ArrayList<>();

    // Chat UI
    private TextView tvChatStatus, tvChatLog;
    private EditText etMessage;
    private Button btnSend, btnBack;
    private ScrollView scrollChat;

    // --- NEARBY VARIABLES ---
    private ConnectionsClient connectionsClient;
    private String myNickname;
    private String connectedEndpointId;
    private String connectedNodeName;
    private boolean isChatting = false;

    static class DiscoveredPeer {
        String endpointId;
        String name;

        DiscoveredPeer(String id, String name) {
            this.endpointId = id;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peer_list);

        // Bind UI
        layoutScanner = findViewById(R.id.layout_scanner);
        layoutChat = findViewById(R.id.layout_chat);

        tvPeerStatus = findViewById(R.id.tvPeerStatus);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvPeers = findViewById(R.id.rvPeers);
        btnScan = findViewById(R.id.btnScan);

        tvChatStatus = findViewById(R.id.tvChatStatus);
        tvChatLog = findViewById(R.id.tvChatLog);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        scrollChat = findViewById(R.id.scrollChat);

        // Setup List
        rvPeers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PeerAdapter(discoveredPeers, peer -> {
            // SENDER LOGIC: INITIATE CALL
            connectTo(peer);
        });
        rvPeers.setAdapter(adapter);

        // Setup Chat Buttons
        btnSend.setOnClickListener(v -> sendMessage());
        btnBack.setOnClickListener(v -> disconnectAndReturn());
        btnScan.setOnClickListener(v -> restartDiscovery());

        myNickname = MeshHelper.getBroadcastName(this);
        tvPeerStatus.setText("My Handle: " + myNickname);

        connectionsClient = Nearby.getConnectionsClient(this);
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
            connectionsClient.stopAllEndpoints();
            connectionsClient.stopAdvertising();
            connectionsClient.stopDiscovery();
        }
    }

    // --- CORE NETWORKING ---

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(v -> {
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Adv Fail", Toast.LENGTH_SHORT).show());
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(v -> tvEmpty.setText("Scanning..."))
                .addOnFailureListener(e -> Toast.makeText(this, "Scan Fail", Toast.LENGTH_SHORT).show());
    }

    private void restartDiscovery() {
        connectionsClient.stopDiscovery();
        discoveredPeers.clear();
        adapter.notifyDataSetChanged();
        startDiscovery();
    }

    private void connectTo(DiscoveredPeer peer) {
        Toast.makeText(this, "Calling " + peer.name + "...", Toast.LENGTH_SHORT).show();
        // Stop discovery to stabilize connection
        connectionsClient.stopDiscovery();

        connectionsClient.requestConnection(myNickname, peer.endpointId, connectionLifecycleCallback)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Call Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    startDiscovery(); // Resume if failed
                });
    }

    private void disconnectAndReturn() {
        if (connectedEndpointId != null) {
            connectionsClient.disconnectFromEndpoint(connectedEndpointId);
        }
        connectedEndpointId = null;
        isChatting = false;

        // Reset UI
        layoutChat.setVisibility(View.GONE);
        layoutScanner.setVisibility(View.VISIBLE);
        tvChatLog.setText("Secure Channel Initialized...\n");

        // Resume Scan
        startDiscovery();
        startAdvertising(); // Re-advertise in case we stopped
    }

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (msg.isEmpty() || connectedEndpointId == null)
            return;

        Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(connectedEndpointId, payload);

        appendChatLog("ME: " + msg);
        etMessage.setText("");
    }

    private void appendChatLog(String text) {
        runOnUiThread(() -> {
            tvChatLog.append(text + "\n");
            scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
        });
    }

    // --- CALLBACKS ---

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            if (isChatting)
                return; // Don't update list while chatting

            boolean exists = false;
            for (DiscoveredPeer p : discoveredPeers) {
                if (p.name.equals(info.getEndpointName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                discoveredPeers.add(new DiscoveredPeer(endpointId, info.getEndpointName()));
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(View.GONE);
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            // Optional cleanup
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            // RECEIVER LOGIC: AUTO-ACCEPT
            // Whether we requested it or they did, we accept.
            connectionsClient.acceptConnection(endpointId, payloadCallback);

            connectedNodeName = info.getEndpointName();
            Toast.makeText(PeerListActivity.this, "Incoming: " + connectedNodeName, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                // SUCCESSFUL CONNECTION
                connectedEndpointId = endpointId;
                isChatting = true;

                // Stop discovery/advertising to save bandwidth/battery and focus on chat
                connectionsClient.stopDiscovery();
                connectionsClient.stopAdvertising();

                // SWITCH UI
                layoutScanner.setVisibility(View.GONE);
                layoutChat.setVisibility(View.VISIBLE);

                tvChatStatus.setText("CONNECTED: " + connectedNodeName);

            } else {
                Toast.makeText(PeerListActivity.this, "Connection Failed", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            if (endpointId.equals(connectedEndpointId)) {
                Toast.makeText(PeerListActivity.this, "Disconnected", Toast.LENGTH_LONG).show();
                disconnectAndReturn();
            }
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);
                String name = connectedNodeName.split("#")[0];
                appendChatLog(name + ": " + msg);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
        }
    };

    // --- ADAPTER ---
    interface OnPeerClickListener {
        void onClick(DiscoveredPeer peer);
    }

    static class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {
        private final List<DiscoveredPeer> list;
        private final OnPeerClickListener listener;

        PeerAdapter(List<DiscoveredPeer> list, OnPeerClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent,
                    false);
            return new PeerViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
            DiscoveredPeer peer = list.get(position);
            holder.text.setText(peer.name);
            holder.itemView.setOnClickListener(v -> listener.onClick(peer));
            // Force text color for visibility on dark theme
            holder.text.setTextColor(0xFF00E5FF); // Cyan
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class PeerViewHolder extends RecyclerView.ViewHolder {
            TextView text;

            PeerViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }
}