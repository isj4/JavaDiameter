package dk.i1.diameter;

/**
 * AVP grouping multiple AVPs together.
 * The following example shows how to construct a grouped AVP.
 * <pre>
Message ccr = ...;
ccr.add(new AVP_Grouped(ProtocolConstants.DI_VENDOR_SPECIFIC_APPLICATION_ID,
                        new AVP[] {
                            new AVP_Unsigned32(ProtocolConstants.DI_VENDOR_ID, my_vendor_id).setM(),
                            new AVP_Unsigned32(ProtocolConstants.DI_ACCT_APPLICATION_ID, my_application_id).setM()
                        }
                       ).setM()
       );
</pre>
 */
public class AVP_Grouped extends AVP {
	public AVP_Grouped(AVP a)  throws InvalidAVPLengthException {
		super(a);
		
		int offset=0;
		byte raw[] = queryPayload();
		int i=0;
		while(offset<raw.length) {
			int avp_sz = AVP.decodeSize(raw,offset,raw.length-offset);
			if(avp_sz==0)
				throw new InvalidAVPLengthException(a);
			offset += avp_sz;
			i++;
		}

		if(offset>raw.length)
			throw new InvalidAVPLengthException(a);
	}
	public AVP_Grouped(int code, AVP... g) {
		super(code,avps2byte(g));
	}
	public AVP_Grouped(int code, int vendor_id, AVP... g) {
		super(code,vendor_id,avps2byte(g));
	}
	
	public AVP[] queryAVPs() {
		int offset=0;
		byte raw[] = queryPayload();
		int i=0;
		while(offset<raw.length) {
			int avp_sz = AVP.decodeSize(raw,offset,raw.length-offset);
			if(avp_sz==0)
				return null;
			offset += avp_sz;
			i++;
		}
		AVP a[] = new AVP[i];
		offset=0;
		i=0;
		while(offset<raw.length) {
			int avp_sz = AVP.decodeSize(raw,offset,raw.length-offset);
			a[i] = new AVP();
			a[i].decode(raw,offset,avp_sz);
			offset+= avp_sz;
			i++;
		}
		return a;
	}
	
	public void setAVPs(AVP... g) {
		setPayload(avps2byte(g));
	}
	
	static private final byte[] avps2byte(AVP g[]) {
		int bytes=0;
		for(AVP a : g) {
			bytes += a.encodeSize();
		}
		byte raw[] = new byte[bytes];
		int offset=0;
		for(AVP a : g) {
			offset += a.encode(raw,offset);
		}
		return raw;
	}
}
