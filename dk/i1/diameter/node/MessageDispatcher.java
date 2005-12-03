package dk.i1.diameter.node;
import dk.i1.diameter.Message;

/**
 * A incoming message dispatcher.
 * A MessageDispatcher is used by the {@link Node} class to dispatch incoming messages.
 * Low-level house-keeping Diameter messages (CEx/DPx/DWx) are not dispatched
 * to it but instead handled by the Node directly.
 * <p>
 * Please note that the handle() method is called by the networking thread and
 * messages from other peers cannot be received until the method returns. If
 * the handle() method needs to do any lengthy processing then it should
 * implement a message queue, put the message into the queue, and return.
 */
public interface MessageDispatcher {
	/**
	 *This method is called when the Node has received a message.
	 *@param msg The incoming message
	 *@param connkey The connection key
	 *@param peer The peer of the connection
	 *@return True if the message was processed. False otherwise, in which case the Node will respond with a error to the peer (if the message was a request).
	 */
	public boolean handle(Message msg, ConnectionKey connkey, Peer peer);
}
