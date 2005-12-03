package dk.i1.diameter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 32-bit floating point AVP
 */
public class AVP_Float32 extends AVP {
	public AVP_Float32(AVP a) throws InvalidAVPLengthException {
		super(a);
		if(a.queryPayloadSize()!=4)
			throw new InvalidAVPLengthException(a);
	}
	
	public AVP_Float32(int code, float value) {
		super(code,float2byte(value));
	}
	public AVP_Float32(int code, int vendor_id, float value) {
		super(code,vendor_id,float2byte(value));
	}
	
	public void setValue(float value) {
		setPayload(float2byte(value));
	}
	
	public float queryValue() {
		byte v[] = queryPayload();
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.put(v);
		bb.rewind();
		return bb.getFloat();
	}
	
	static private final byte[] float2byte(float value) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putFloat(value);
		bb.rewind();
		byte v[] = new byte[4];
		bb.get(v);
		return v;
	}
}
