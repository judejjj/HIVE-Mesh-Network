# 🐝 HIVE: Decentralized Mesh Network

![Status](https://img.shields.io/badge/Status-Development-yellow)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![API](https://img.shields.io/badge/API-Google%20Nearby%20Connections-blue)

**HIVE** is an Android-based communication framework designed to operate in **zero-connectivity environments**. It turns smartphones into a self-healing mesh network, allowing users to chat without Internet, SIM cards, or centralized servers.

## 🚀 Key Features
* **Zero Infrastructure:** Works entirely offline using Wi-Fi Direct & Bluetooth LE.
* **Mesh Topology:** Implements a **Flooding Algorithm** to relay messages across multiple devices (Multi-Hop).
* **Auto-Connect:** Uses `Strategy.P2P_CLUSTER` for automatic neighbor discovery and connection.
* **Resilient:** No single point of failure; if one node drops, the network reroutes.

## 🛠️ Tech Stack
* **Language:** Java / Kotlin
* **IDE:** Android Studio Otter (2025.2.2)
* **Core API:** Google Nearby Connections API v19.0.0+
* **Architecture:** MVVM (Model-View-ViewModel)

## 📱 How It Works
1.  **Discovery:** Devices constantly advertise and scan for nearby peers using BLE.
2.  **Handshake:** Automatic connection establishment via Wi-Fi Direct (High Bandwidth).
3.  **Routing:** * User A sends a message.
    * User B (Relay) receives it, checks the UUID, and broadcasts it to User C.
    * User C receives the message instantly.

## 📦 Setup & Installation
1.  Clone this repository.
2.  Open in **Android Studio Otter**.
3.  Connect 2 or more physical Android devices via USB Debugging.
4.  Build & Run (`Shift + F10`).
5.  *Note: Permissions for Location and Bluetooth must be granted manually if not prompted.*

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Final Year Engineering Project | 2025*
