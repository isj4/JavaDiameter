package dk.i1.diameter;

/**
 * Exception thrown when an AVP does not have the correct size.
 */
public class InvalidAVPLengthException extends Exception {
	/**The AVP that did not have the correct size of its expected type. This can later be wrapped into an Failed-AVP AVP.*/
	public AVP avp;
	/**Construct the expection with the specified AVP*/
	public InvalidAVPLengthException(AVP avp) {
		this.avp = new AVP(avp);
	}
}
