package dk.i1.diameter;

/**
 * Exception thrown when an AVP_Address is constructed from unsupported on-the-wire content.
 */
public class InvalidAddressTypeException extends Exception {
	/**The AVP that did not have the correct size/type of its expected type. This can later be wrapped into an Failed-AVP AVP.*/
	public AVP avp;
	/**Construct the expection with the specified AVP*/
	public InvalidAddressTypeException(AVP avp) {
		this.avp = new AVP(avp);
	}
}
