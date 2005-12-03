package dk.i1.diameter;

/**
 * 64-bit signed intege rAVP.
 */
public class AVP_Integer64 extends AVP {
	public AVP_Integer64(AVP a) throws InvalidAVPLengthException {
		super(a);
		if(a.queryPayloadSize()!=8)
			throw new InvalidAVPLengthException(a);
	}
	public AVP_Integer64(int code, long value) {
		super(code,long2byte(value));
	}
	public AVP_Integer64(int code, int vendor_id, long value) {
		super(code,vendor_id,long2byte(value));
	}
	public long queryValue() {
		return packunpack.unpack64(payload,0);
	}
	public void setValue(long value) {
		packunpack.pack64(payload,0,value);
	}
	static private final byte[] long2byte(long value) {
		byte[] v=new byte[8];
		packunpack.pack64(v,0,value);
		return v;
	}
}
