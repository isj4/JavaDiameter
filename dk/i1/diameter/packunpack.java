package dk.i1.diameter;

class packunpack {
	public static final void pack8(byte b[], int offset, byte value) {
		b[offset] = value;
	}
	public static final void pack16(byte b[], int offset, int value) {
		b[offset+0] = (byte)((value>> 8)&0xFF);
		b[offset+1] = (byte)((value    )&0xFF);
	}
	public static final void pack32(byte b[], int offset, int value) {
		b[offset+0] = (byte)((value>>24)&0xFF);
		b[offset+1] = (byte)((value>>16)&0xFF);
		b[offset+2] = (byte)((value>> 8)&0xFF);
		b[offset+3] = (byte)((value    )&0xFF);
	}
	public static final void pack64(byte b[], int offset, long value) {
		b[offset+0] = (byte)((value>>56)&0xFF);
		b[offset+1] = (byte)((value>>48)&0xFF);
		b[offset+2] = (byte)((value>>40)&0xFF);
		b[offset+3] = (byte)((value>>32)&0xFF);
		b[offset+4] = (byte)((value>>24)&0xFF);
		b[offset+5] = (byte)((value>>16)&0xFF);
		b[offset+6] = (byte)((value>> 8)&0xFF);
		b[offset+7] = (byte)((value    )&0xFF);
	}

	public static final byte unpack8(byte b[], int offset) {
		return b[offset];
	}
	public static final int unpack32(byte b[], int offset) {
		return ((b[offset+0]&0xFF)<<24)
		     | ((b[offset+1]&0xFF)<<16)
		     | ((b[offset+2]&0xFF)<< 8)
		     | ((b[offset+3]&0xFF)    )
		     ;
	}
	public static final int unpack16(byte b[], int offset) {
		return ((b[offset+0]&0xFF)<< 8)
		     | ((b[offset+1]&0xFF)    )
		     ;
	}
	public static final long unpack64(byte b[], int offset) {
		return ((long)(b[offset+0]&0xFF)<<56)
		     | ((long)(b[offset+1]&0xFF)<<48)
		     | ((long)(b[offset+2]&0xFF)<<40)
		     | ((long)(b[offset+3]&0xFF)<<32)
		     | ((long)(b[offset+4]&0xFF)<<24)
		     | ((long)(b[offset+5]&0xFF)<<16)
		     | ((long)(b[offset+6]&0xFF)<< 8)
		     | ((long)(b[offset+7]&0xFF)    )
		     ;
	}
}
