package dk.i1.diameter.node;
import dk.i1.diameter.node.Capability;

/**
 * Validate peers and their claimed capabilities
 * The implementations of NodeValidator handle the verification that we
 * know the node(s) when they connect, and calculate the resulting
 * capabilities/roles we allow the nodes.
 * If you do not provide the Node instance a node validator instance then
 * the node instance will use a {@link DefaultNodeValidator}.
 * Implementations can implement a "peer list" using plain-text files, databases, etc.
 * @since 0.9.4
 */
public interface NodeValidator {
	static public class AuthenticationResult {
		/** Do we know this node? */
		public boolean known;
		/** If not known, which Error-Message should be included in the reject message? (can be null) */
		public String error_message;
		/** If not known, what should hte Result-Code be? (Node instance defaults to 3010) */
		public Integer result_code;
	};
	/** Verify that we know the node.
	 * This method is called when a peer connects and tells us its name in a CER.
	 * The implementation should return an {@link AuthenticationResult}
	 * telling the node if we know the peer, and if not what the
	 * result-code and error-message should be. (Node provides reasonable defaults).
	 * @param host_id The orogin-host-id of the peer.
	 * @param obj An object describing the transport connection. Currently always a socket channel.
	 */
	public AuthenticationResult authenticateNode(String host_id, Object obj);
	/**
	 * Calculate the capabilities that we allow the peer to have.
	 * This method is called after the node has been authenticated.
	 * Note: This method is also called for outbound connections.
	 * @param host_id The origin-host-id of the peer.
	 * @param settings The settings of the node (as passed to its constructor)
	 * @param reported_capabilities The capability set the peer reported it supports.
	 */
	public Capability authorizeNode(String host_id, NodeSettings settings, Capability reported_capabilities);
}
