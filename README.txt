Build Instructions
==================

Run in terminal:
javac *.java
java LocalTest

Working Functionality
=====================

- Name messages (G/H) - responding to name requests
- Nearest messages (N/O) - returning closest known nodes
- Key existence (E/F) - checking if a key is stored
- Read (R/S) - reading a value for a key
- Write (W/X) - storing key/value pairs
- Compare-and-swap (C/D) - atomic value update
- Relay (V) - forwarding messages to other nodes
- Retrying requests up to 3 times if no response after 5 seconds
- Duplicate packets are detected and ignored
- Packet reordering handled via transaction ID matching
- 3 per distance rule enforced for address storage
- Relay stack used when sending messages
- Malformed messages don't crash the node

Known Limitations:
- Active node discovery via Nearest requests not implemented

