# Multi-client-server-messaging-system #

**REPORT Project Phase 1:** [link](https://github.com/anubhav-cs/Multi-client-server-messaging-system/blob/master/Project_Report_1.pdf)

**REPORT Project Extension:** [link](https://github.com/anubhav-cs/Multi-client-server-messaging-system/blob/master/Design-and-analysis.pdf)

The project deals with building a multi-server system that primarily supports registering and logging in clients, authenticating servers, load balancing clients on the available servers using a redirection mechanism and broadcasting activity objects from one client to all other clients.

The system was designed with Super-peer model at its heart. A super-peer network contains elements of both pure peer-to-peer and centralized model. Allowing for peer-to-peer elements such as autonomy and availability, as well as, benefits of centralized approach in tasks requiring consistency and optimized querying servers for information. In this model, the super-peer acts as a centralized server for their peers, whereas the connection model between the super-peers is that of a peer-to-peer. The super-peers are connected in a mesh network and each normal-peer connect to one of the super-peers.

# Walkthrough of the Multi-Server system #

1. Start a new server (with `-s password`)
It would set up a listener on localhost:3780. After which, it would check for given remote hostname and port, and if it doesn't find any, it will automatically raise itself to be the master-
super-peer.

2. If we start a new client now with (-u aaron), it will try to make a register request directly at the first server (Server A).
Since A is a master-super-peer, it will instantly respond to the register request. Since aaron username does not exist in the system, it will register it, send back a REGISTER_SUCCESS
and try to broadcast the REGISTER_SUCCESS to other super-peers.

3. If we now start up a new server with the cmd arguments
`-s password -lp 3781 -rh localhost - rp 3780`
It will initiate a connection with Server A, be successful and connect as a peer. Then, since the number of super-peers in the system is less than the minimum threshold specified in settings (in our code, this is 1), Server A will try to raise this server (Server B) to a super-peer level. During this raising process, all the registered clients (aaron) are sent to Server B. Now, the
user aaron is stored in B, with the attached secret as well as its current logged-in state, which is true. So, if aaron tries to login right now to Server B, it will fail.

4. With these cmd line arguments,
`-u aaron -s kfcis614r23ov6v1tbahcididb -rp 3781`
Once aaron has logged out (or disconnected) from server A, this information will be shared with server B and then aaron will be able to login at server B. However, if aaron tries to register again, at any server, he will receive REGISTER_FAILED. If we start another server now, then it will not be raised to Superpeer (because we have specified the threshold to be just 1). Instead it will remain a peer, and only stay connected to one server.

  Server:`-s password -lp 3784 -rh localhost -rp 3780`

  Client:`-u hello -rp 3784`

  Client:`-u hello`

5. A for guarantees that all activity messages sent by a client are delivered in the same order at each receiving client, this is done by implementing a Comparator which compares all pending messages at the moment, and stores them for a user in the given order whenever this user exits.
```
class Sortbyusernamesequence implements Comparator<StoredMessage> {
    public int compare(StoredMessage a, StoredMessage b) {
        int usernameComparison = a.getUsername().compareTo(b.getUsername());
        if (usernameComparison == 0)
            return a.getSequence() - b.getSequence();
        return usernameComparison;
    }
}
```

  This means that a few extra messages that the client might have already received will be sent
  to them again, the next time they login to the system. To test this, you would need to broadcast
  a message, and disconnect/logout the user before the message delivery is complete
  anywhere in the system.
