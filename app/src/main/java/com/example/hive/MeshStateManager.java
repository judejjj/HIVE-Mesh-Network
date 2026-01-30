package com.example.hive;

import java.util.ArrayList;
import java.util.List;

public class MeshStateManager {
    // Shared list of peers (Name + ID)
    public static final List<Peer> connectedPeers = new ArrayList<>();

    public static class Peer {
        public String id;
        public String name;

        public Peer(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
