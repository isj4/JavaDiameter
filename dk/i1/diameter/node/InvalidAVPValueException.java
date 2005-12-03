package dk.i1.diameter.node;
import dk.i1.diameter.AVP;

class InvalidAVPValueException extends Exception {
	public AVP avp;
	public InvalidAVPValueException(AVP avp) {
		this.avp=avp;
	}
}
