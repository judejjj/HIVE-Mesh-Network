package com.example.hive;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrivateChatActivity extends AppCompatActivity {

    private static final String SERVICE_ID = "com.example.hive";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private TextView tvTargetName;
    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;

    private ChatAdapter adapter;
    private final List<String> messages = new ArrayList<>();

    private ConnectionsClient connectionsClient;
    private String myNickname;
    private String targetName;
    private String targetEndpointId;

    private final Set<String> discoveredEndpoints = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        tvTargetName = findViewById(R.id.tvTargetName);
        rvChat = findViewById(R.id.rvPrivateChat);
        etMessage = findViewById(R.id.etPrivateMessage);
        btnSend = findViewById(R.id.btnSendPrivate);

        // GET TARGET FROM INTENT
        targetName = getIntent().getStringExtra("TARGET_NAME");
        if (targetName == null)
            targetName = "Unknown Target";

        tvTargetName.setText("SNIPING: " + targetName);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        rvChat.setAdapter(adapter);

        // Load Identity via Helper
        myNickname = MeshHelper.getBroadcastName(this);

        connectionsClient = Nearby.getConnectionsClient(this);

        btnSend.setOnClickListener(v -> sendMessage());

        addSystemMessage("Initializing Sniper Mode...");
        addSystemMessage("Target: " + targetName);
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

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener(e -> addSystemMessage("Adv Fail: " + e.getMessage()));
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnFailureListener(e -> addSystemMessage("Scan Fail: " + e.getMessage()));
    }

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (msg.isEmpty())
            return;

        if (targetEndpointId == null) {
            addSystemMessage("Target not connected.");
            return;
        }

        Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(targetEndpointId, payload);

        addMessage("ME: " + msg);
        etMessage.setText("");
    }

    private void addMessage(String text) {
        runOnUiThread(() -> {
            messages.add(text);
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    private void addSystemMessage(String text) {
        addMessage("SYS: " + text);
    }

    // --- CALLBACKS ---

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            if (discoveredEndpoints.contains(endpointId))
                return;
            discoveredEndpoints.add(endpointId);

            String foundName = info.getEndpointName();
            // SNIPER CHECK
            if (foundName.equals(targetName)) {
                addSystemMessage("Target Located: " + foundName);
                connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                        .addOnFailureListener(e -> addSystemMessage("Req Fail: " + e.getMessage()));
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            discoveredEndpoints.remove(endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            // Check if this is who we want (Either they called us, or we called them)
            if (info.getEndpointName().equals(targetName) || targetName.equals(info.getEndpointName())) {
                connectionsClient.acceptConnection(endpointId, payloadCallback);
                addSystemMessage("Accepting Target Link...");
            } else {
                if (targetName.equals(info.getEndpointName())) { // paranoia check
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                } else {
                    // For Private Chat, we typically REJECT anyone else
                    // But to avoid blocking accidental connections, we might accept.
                    // The user said: "NO: Do nothing (Ignore other peers)" in Discovery.
                    // But in Initiated, if someone ELSE calls us, we should probably ignore/reject?
                    // Let's accept for robustness but we won't show their messages if we filter?
                    // No, let's accept.
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }
            }
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                targetEndpointId = endpointId;
                addSystemMessage(">>> SECURE UPLINK ESTABLISHED");
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            addSystemMessage("Link Broken.");
            if (endpointId.equals(targetEndpointId))
                targetEndpointId = null;
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);
                String displayName = targetName.split("#")[0];
                addMessage(displayName + ": " + msg);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
        }
    };

    // --- ADAPTER ---
    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
        private final List<String> msgs;

        ChatAdapter(List<String> msgs) {
            this.msgs = msgs;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent,
                    false);
            return new ChatViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            String msg = msgs.get(position);
            holder.text.setText(msg);
            holder.text.setTextColor(Color.WHITE);

            if (msg.startsWith("ME:")) {
                holder.text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                holder.text.setTextColor(Color.GREEN);
            } else if (msg.startsWith("SYS:")) {
                holder.text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                holder.text.setTextColor(Color.GRAY);
                holder.text.setTextSize(12);
            } else {
                holder.text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                holder.text.setTextColor(Color.CYAN);
            }
        }

        @Override
        public int getItemCount() {
            return msgs.size();
        }

        static class ChatViewHolder extends RecyclerView.ViewHolder {
            TextView text;

            ChatViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }
}
