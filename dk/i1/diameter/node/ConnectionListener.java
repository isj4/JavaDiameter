package dk.i1.diameter.node;

/**
 * A connection setup/tear-down observer.
 * The ConnectionListener interface is used by the {@link Node} class to
 * signal that a connection has been established (CER/CEA has been sucessfully
 * exchanged) or that a connection has been lost (Due to DPR or broken
 * transport connection)
 */
public interface ConnectionListener {
	/**
	 * A connection has changed state.
	 * If up is false then connkey is no longer valid (connection lost).
	 * @param connkey The connection key.
	 * @param peer The peer the connection is to.
	 * @param up True if the connection has been established. False if the connection has been lost.
	 */
	public void handle(ConnectionKey connkey, Peer peer, boolean up);
}
