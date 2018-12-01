package activitystreamer.server;

public enum ServerState {
	PEER, 
	PEERTOSUPERPEER, // when being changed from peer to superpeer
	SUPERPEER, 
	MASTERSUPERPEER;

}
