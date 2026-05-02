// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  Seyer Raji
//  240023389
//  seyer.raji@city.ac.uk

import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Stack;


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

interface NodeInterface {

    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */
    
    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;
    
    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.
    
    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;
    

    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;
    
    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends

// Complete this!
public class Node implements NodeInterface {

    String nodeName;
    DatagramSocket socket;
    HashMap<String, String> keyValueStore = new HashMap<>();
    HashMap<String, String> addressPairs = new HashMap<>();
    Stack<String> relayStack = new Stack<>();
    HashSet<String> seenTransactionIDs = new HashSet<>();

    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
    }

    public void openPort(int portNumber) throws Exception {
        socket = new DatagramSocket(portNumber);
        String myAddress = InetAddress.getLocalHost().getHostAddress() + ":" + portNumber;
        addressPairs.put(nodeName, myAddress);
    }

    public void handleIncomingMessages(int delay) throws Exception {
        socket.setSoTimeout(delay);

        while (true) {
            try {
                byte[] buffer = new byte[65536];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());

                String transactionID = message.substring(0,2);
                char messageType = message.charAt(3);

                if (seenTransactionIDs.contains(transactionID)) {
                    continue;
                }
                seenTransactionIDs.add(transactionID);

                switch (messageType) {
                    case 'G': {
                        String responseMessage = transactionID + " H " + encode(nodeName);
                        byte[] response = responseMessage.getBytes();
                        DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(reply);
                        break;
                    }
                    case 'N': {
                        String hashID = message.substring(4);
                        List<String> closest = findClosestThree(hashID);

                        String responseMessage = transactionID + " O ";
                        for (String node : closest) {
                            responseMessage += encode(node) + encode(addressPairs.get(node));
                        }
                        byte[] response = responseMessage.getBytes();
                        DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(reply);
                        break;
                    }
                    case 'E': {
                        String firstString = message.substring(5);
                        String key = decodeFirstMessage(firstString);
                        String responseCode;
                        if (keyValueStore.containsKey(key)) {
                            responseCode = "Y";
                        } else {
                            responseCode = "N";
                        }
                        String responseMessage = transactionID + " F " + responseCode + " ";
                        byte[] response = responseMessage.getBytes();
                        DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(reply);
                        break;
                    }
                    case 'R': {
                        String firstString = message.substring(5);
                        String key = decodeFirstMessage(firstString);
                        if (keyValueStore.containsKey(key)) {
                            String value = keyValueStore.get(key);
                            String responseMessage = transactionID + " S Y " + encode(value);
                            byte[] response = responseMessage.getBytes();
                            DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                            socket.send(reply);
                        } else {
                            String responseMessage = transactionID + " S N ";
                            byte[] response = responseMessage.getBytes();
                            DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                            socket.send(reply);
                        }
                        break;
                    }
                    case 'W': {
                        String firstString = message.substring(5);
                        String key = decodeFirstMessage(firstString);
                        String value = decodeFirstMessage(decodeRemainingMessage(firstString));
                        String responseCode;
                        if (key.startsWith("N:")) {
                            if (addressPairs.containsKey(key)) {
                                responseCode = "R";
                                addressPairs.put(key, value);
                            } else if (canStoreAddress(key)) {
                                responseCode = "A";
                                addressPairs.put(key, value);
                            } else {
                                responseCode = "A";
                            }
                        } else {
                            if (keyValueStore.containsKey(key)) {
                                responseCode = "R";
                            } else {
                                responseCode = "A";
                            }
                            keyValueStore.put(key, value);
                        }
                        String responseMessage = transactionID + " X " + responseCode + " ";
                        byte[] response = responseMessage.getBytes();
                        DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(reply);
                        break;
                    }
                    case 'V': {
                        String rest = message.substring(5);
                        String relayTarget = decodeFirstMessage(rest);
                        String innerMessage = decodeRemainingMessage(rest);

                        String ipPort = addressPairs.get(relayTarget);
                        if (ipPort != null) {
                            int colonIndex = ipPort.indexOf(":");
                            String ip = ipPort.substring(0, colonIndex);
                            int port = Integer.parseInt(ipPort.substring(colonIndex + 1));
                            InetAddress address = InetAddress.getByName(ip);

                            String relayResponse = sendAndReceive(innerMessage, address, port);
                            if (relayResponse != null) {
                                String responseMessage = transactionID + " " + relayResponse.substring(3);
                                byte[] responseBytes = responseMessage.getBytes();
                                DatagramPacket reply = new DatagramPacket(responseBytes, responseBytes.length, packet.getAddress(), packet.getPort());
                                socket.send(reply);
                            }
                        }
                        break;
                    }
                    case 'C': {
                        String rest = message.substring(5);
                        String key = decodeFirstMessage(rest);
                        String currentValue = decodeFirstMessage(decodeRemainingMessage(rest));
                        String newValue = decodeFirstMessage(decodeRemainingMessage(decodeRemainingMessage(rest)));

                        String responseCode;
                        if (keyValueStore.containsKey(key)) {
                            if (keyValueStore.get(key).equals(currentValue)) {
                                keyValueStore.put(key, newValue);
                                responseCode = "R";
                            } else {
                                responseCode = "N";
                            }
                        } else {
                            responseCode = "A";
                        }
                        String responseMessage = transactionID + " D " + responseCode + " ";
                        byte[] response = responseMessage.getBytes();
                        DatagramPacket reply = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(reply);
                        break;
                    }
                    default:
                        break;
                }

            } catch (SocketTimeoutException error) {
                return;
            } catch (Exception error) {
            }
        }
    }

    public boolean isActive(String nodeName) throws Exception {
        String ipPort = addressPairs.get(nodeName);
        if (ipPort == null) {
            return false;
        }
        int colonIndex = ipPort.indexOf(":");
        String ip = ipPort.substring(0, colonIndex);
        int port = Integer.parseInt(ipPort.substring(colonIndex + 1));

        InetAddress address = InetAddress.getByName(ip);
        String reply = sendAndReceive(createTransactionID() + " G ", address, port);
        if (reply != null && reply.charAt(3) == 'H') {
            return true;
        }
        return false;
    }

    public void pushRelay(String nodeName) throws Exception {
        relayStack.push(nodeName);
    }

    public void popRelay() throws Exception {
        if (!relayStack.isEmpty()) relayStack.pop();
    }

    public boolean exists(String key) throws Exception {
        handleIncomingMessages(100);
        List<String> closest = findClosestThree(key);
        for (String node : closest) {
            String ipPort = addressPairs.get(node);
            int colonIndex = ipPort.indexOf(":");
            String ip = ipPort.substring(0, colonIndex);
            int port = Integer.parseInt(ipPort.substring(colonIndex + 1));
            InetAddress address = InetAddress.getByName(ip);

            String request = createTransactionID() + " E " + encode(key);
            String response = sendAndReceive(request, address, port);
            if (response != null && response.contains(" Y ")) {
                return true;
            }
        }
        return false;
    }

    public String read(String key) throws Exception {
        handleIncomingMessages(100);
        List<String> closest = findClosestThree(key);
        for (String node : closest) {
            String ipPort = addressPairs.get(node);
            int colonIndex = ipPort.indexOf(":");
            String ip = ipPort.substring(0, colonIndex);
            int port = Integer.parseInt(ipPort.substring(colonIndex + 1));
            InetAddress address = InetAddress.getByName(ip);

            String request = createTransactionID() + " R " + encode(key);
            String response = sendAndReceive(request, address, port);
            if (response != null && response.charAt(5) == 'Y') {
                return decodeFirstMessage(response.substring(7));
            }
        }
        return null;
    }

    public boolean write(String key, String value) throws Exception {
        handleIncomingMessages(100);
        List<String> closest = findClosestThree(key);
        boolean success = false;
        for (String node : closest) {
            String ipPort = addressPairs.get(node);
            int colonIndex = ipPort.indexOf(":");
            String ip = ipPort.substring(0, colonIndex);
            int port = Integer.parseInt(ipPort.substring(colonIndex + 1));
            InetAddress address = InetAddress.getByName(ip);

            String request = createTransactionID() + " W " + encode(key) + encode(value);
            String response = sendAndReceive(request, address, port);
            if (response != null && (response.contains(" R ") || response.contains(" A "))) {
                success = true;
            }
        }
        return success;
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        handleIncomingMessages(100);
        List<String> closest = findClosestThree(key);
        for (String node : closest) {
            String ipPort = addressPairs.get(node);
            int colonIndex = ipPort.indexOf(":");
            String ip = ipPort.substring(0, colonIndex);
            int port = Integer.parseInt(ipPort.substring(colonIndex + 1));
            InetAddress address = InetAddress.getByName(ip);

            String request = createTransactionID() + " C " + encode(key) + encode(currentValue) + encode(newValue);
            String response = sendAndReceive(request, address, port);
            if (response != null && response.contains(" R ")) {
                return true;
            }
        }
        return false;
    }

    int compareMatchingBits(String key1, String key2) throws Exception {
        byte[] hashA = HashID.computeHashID(key1);
        byte[] hashB = HashID.computeHashID(key2);
        int count = 0;

        for (int i = 0; i < 32; i++) {
            if (hashA[i] == hashB[i]) count += 8;
            else {
                for (int j = 0; j < 8; j++) {
                    int bitIndex = 7 - j;
                    int bitA = (hashA[i] >> bitIndex) & 1;
                    int bitB = (hashB[i] >> bitIndex) & 1;

                    if (bitA == bitB) {
                        count++;
                    } else {
                        return count;
                    }
                }
            }
        }

        return count;
    }

    String encode(String s) {
        int spaceCount = 0;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                spaceCount++;
            }
        }
        return (spaceCount + " " + s + " ");
    }

    String decode(String s) {
        int firstSpaceIndex = s.indexOf(' ');
        String message = s.substring(firstSpaceIndex + 1, s.length() -1);

        return message;
    }

    String decodeFirstMessage(String s) {
        int spaceCount = Character.getNumericValue(s.charAt(0));
        int spacesFound = 0;
        int end = 2;
        for (int i = 2; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                spacesFound++;
                if (spacesFound == spaceCount + 1) {
                    end = i;
                    break;
                }
            }
        }
        return decode(s.substring(0, end + 1));
    }

    String decodeRemainingMessage(String s) {
        int spaceCount = Character.getNumericValue(s.charAt(0));
        int spacesFound = 0;
        for (int i = 2; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                spacesFound++;
                if (spacesFound == spaceCount + 1) {
                    return s.substring(i + 1);
                }
            }
        }
        return "";
    }

    String sendAndReceive(String message, InetAddress address, int port) throws Exception {
        if (!relayStack.isEmpty()) {
            String relayNode = relayStack.peek();
            String relayIpPort = addressPairs.get(relayNode);
            if (relayIpPort != null) {
                int colonIndex = relayIpPort.indexOf(":");
                String relayIp = relayIpPort.substring(0, colonIndex);
                int relayPort = Integer.parseInt(relayIpPort.substring(colonIndex + 1));
                address = InetAddress.getByName(relayIp);
                port = relayPort;
                message = message.substring(0, 2) + " V " + encode(relayNode) + message.substring(3);
            }
        }

        DatagramSocket tempSocket = new DatagramSocket();
        tempSocket.setSoTimeout(5000);
        byte[] requestBytes = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(requestBytes, requestBytes.length, address, port);

        String transactionID = message.substring(0, 2);

        for (int attempt = 0; attempt < 3; attempt++) {
            tempSocket.send(sendPacket);
            try {
                while (true) {
                    byte[] buffer = new byte[65536];
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    tempSocket.receive(receivePacket);
                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (response.length() >= 2 && response.substring(0, 2).equals(transactionID)) {
                        tempSocket.close();
                        return response;
                    }
                }
            } catch (SocketTimeoutException e) {
            }
        }
        tempSocket.close();
        return null;
    }

    String createTransactionID() {
        Random random = new Random();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        char first = chars.charAt(random.nextInt(chars.length()));
        char second = chars.charAt(random.nextInt(chars.length()));
        return "" + first + second;
    }

    boolean canStoreAddress(String newNode) throws Exception {
        int distance = compareMatchingBits(nodeName, newNode);
        int count = 0;
        for (String existing : addressPairs.keySet()) {
            if (compareMatchingBits(nodeName, existing) == distance) {
                count++;
            }
        }
        return count < 3;
    }

    List<String> findClosestThree(String key) throws Exception {
        List<String> nodes = new ArrayList<>(addressPairs.keySet());
        List<String> closest = new ArrayList<>();

        for (int i = 0; i < 3 && !nodes.isEmpty(); i++) {
            String closestNode = null;
            int mostMatchingBits = -1;
            for (String node : nodes) {
                int bits = compareMatchingBits(node, key);
                if (bits > mostMatchingBits) {
                    mostMatchingBits = bits;
                    closestNode = node;
                }
            }
            closest.add(closestNode);
            nodes.remove(closestNode);
        }
        return closest;
    }

}
