package activitystreamer.server;

public enum ConType {
	NEW,
	CLIENT,
	PEER,
	PEERTOSUPERPEER,	// a peer in the process of becoming a superpeer
	SUPERPEER;
}
