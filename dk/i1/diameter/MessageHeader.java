package dk.i1.diameter;

/**
 * A Diameter message header.
 * See RFC3588 section 3. After you have read that understanding the class is trivial.
 * The only fields and methods you will normally use are:
 * <ul>
 *   <li>{@link #command_code}</li>
 *   <li>{@link #application_id} (maybe)</li>
 *   <li>{@link #setProxiable}</li>
 *   <li>{@link #setRequest}</li>
 * </ul>
 * Note: The default command flags does not include the proxiable bit, meaning
 * that request messages by default cannot be proxied by diameter proxies and
 * other gateways. It is still not determined if this is a reasonable default.
 * You should always call setProxiable() explicitly so it has the value you
 * expect it to be.
 */
public class MessageHeader {
	byte version;
	//int message_length;
	private byte command_flags;
	public int command_code;
	public int application_id;
	public int hop_by_hop_identifier;
	public int end_to_end_identifier;
	
	public static final byte command_flag_request_bit    = (byte)0x80;
	public static final byte command_flag_proxiable_bit  = 0x40;
	public static final byte command_flag_error_bit      = 0x20;
	public static final byte command_flag_retransmit_bit = 0x10;
	
	public boolean isRequest() {
        	return (command_flags&command_flag_request_bit)!=0;
	}
	public boolean isProxiable() {
        	return (command_flags&command_flag_proxiable_bit)!=0;
	}
	public boolean isError() {
        	return (command_flags&command_flag_error_bit)!=0;
	}
	public boolean isRetransmit() {
        	return (command_flags&command_flag_retransmit_bit)!=0;
	}
	public void setRequest(boolean b) {
		if(b)
			command_flags |= command_flag_request_bit;
		else
			command_flags &= ~command_flag_request_bit;
	}
	public void setProxiable(boolean b) {
		if(b)
			command_flags |= command_flag_proxiable_bit;
		else
			command_flags &= ~command_flag_proxiable_bit;
	}
	/**Set error bit. See RFC3588 section 3 page 32 before you set this.*/
	public void setError(boolean b) {
		if(b)
			command_flags |= command_flag_error_bit;
		else
			command_flags &= ~command_flag_error_bit;
	}
	/**Set retransmit bit*/
	public void setRetransmit(boolean b) {
		if(b)
			command_flags |= command_flag_retransmit_bit;
		else
			command_flags &= ~command_flag_retransmit_bit;
	}
	
	/**
	 * Default constructor for MessageHeader.
	 * The command flags are initialized to answer+not-proxiable+not-error+not-retransmit, also known as 0.
	 * The command_code, application_id, hop_by_hop_identifier and end_to_end_identifier are initialized to 0.
	 */
	public MessageHeader() {
		version = 1;
	}
	
	/**
	 * Copy-constructor for MessageHeader.
	 * Implements a deep copy.
	 */
	public MessageHeader(MessageHeader mh) {
		version = mh.version;
		command_flags = mh.command_flags;
		command_code = mh.command_code;
		application_id = mh.application_id;
		hop_by_hop_identifier = mh.hop_by_hop_identifier;
		end_to_end_identifier = mh.end_to_end_identifier;
	}
	
	int encodeSize() {
		return 5*4;
	}
	int encode(byte b[], int offset, int message_length) {
		packunpack.pack32(b, offset+ 0, message_length);
		packunpack.pack8 (b, offset+ 0, version);
		packunpack.pack32(b, offset+ 4, command_code);
		packunpack.pack8 (b, offset+ 4, command_flags);
		packunpack.pack32(b, offset+ 8, application_id);
		packunpack.pack32(b, offset+12, hop_by_hop_identifier);
		packunpack.pack32(b, offset+16, end_to_end_identifier);
		return 5*4;
	}
	
	void decode(byte b[], int offset) {
		version = packunpack.unpack8(b, offset+0);
		//message_length = Array.getInt(b,offset+0)&0x00FFFFFF;
		command_flags = packunpack.unpack8(b,offset+4);
		command_code = packunpack.unpack32(b,offset+4)&0x00FFFFFF;
		application_id = packunpack.unpack32(b,offset+8);
		hop_by_hop_identifier = packunpack.unpack32(b,offset+12);
		end_to_end_identifier = packunpack.unpack32(b,offset+16);
	}
	
	/**
	 * Prepare a response from the specified request header.
	 * The proxiable flag is copied - the other flags are cleared.
	 * The command_code, application_id, hop_by_hop_identifier,
	 * end_to_end_identifier and 'proxyable' command flag
	 * are copied. The 'request', 'error' and 'retransmit' bits are cleared.
	 */
	public void prepareResponse(MessageHeader request) {
		command_flags = (byte)(request.command_flags&command_flag_proxiable_bit);
		command_code = request.command_code;
		application_id = request.application_id;
		hop_by_hop_identifier = request.hop_by_hop_identifier;
		end_to_end_identifier = request.end_to_end_identifier;
	}
	
	/**
	 * Prepare an answer from the specified request header.
	 * This is identical to prepareResponse().
	 * @since 0.9.3
	 */
	public void prepareAnswer(MessageHeader request) {
		prepareResponse(request);
	}
}
