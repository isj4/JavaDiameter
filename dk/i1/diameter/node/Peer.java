package dk.i1.diameter.node;
import java.net.*;
import java.util.StringTokenizer;

/**
 * A Diameter peer.
 * Hmmmm. more documentation here...
 */
public class Peer {
	private String host;
	private int port;
	private boolean secure;
	public enum TransportProtocol {
		tcp,
		sctp
	};
	TransportProtocol transport_protocol;
	
	/**
	 * Constructs a peer from an IP address.
	 * The address is set to the specified address, the port is set to 3868,
	 * and the secure setting is off.
	 * @param address The IP address of the peer.
	 */
	public Peer(InetAddress address) {
		this(address,TransportProtocol.tcp);
	}
	/**
	 * Constructs a peer from an IP address and transport-protocol.
	 * The address is set to the specified address, the port is set to 3868,
	 * and the secure setting is off.
	 * @param address The IP address of the peer.
	 * @param transport_protocol TCP or SCTP
	 * @since 0.9.5
	 */
	public Peer(InetAddress address, TransportProtocol transport_protocol) {
		this(address,3868,transport_protocol);
	}
	/**
	 * Constructs a peer from an IP address.
	 * The address is set to the specified address, the port is set to the
	 * specified port, and the secure setting is off.
	 * @param address The IP address of the peer.
	 * @param port The port of the peer.
	 */
	public Peer(InetAddress address, int port) {
		this(address,port,TransportProtocol.tcp);
	}
	/**
	 * Constructs a peer from IP address + port + transport-protocol.
	 * The address is set to the specified address, the port is set to the
	 * specified port, and the secure setting is off.
	 * @param address The IP address of the peer.
	 * @param port The port of the peer.
	 * @param transport_protocol TCP or SCTP
	 * @since 0.9.5
	 */
	public Peer(InetAddress address, int port, TransportProtocol transport_protocol) {
		this.host = address.getHostAddress();
		this.port = port;
		this.secure = false;
		this.transport_protocol = transport_protocol;
	}
	/**
	 * Constructs a peer from a host name.
	 * The address is set to the specified host-name. The IP-address of the
	 * host is not immediately resolved. The port is set to 3868,
	 * and the secure setting is off.
	 * @param host The host-name of the peer (preferably fully-qualified)
	 */
	public Peer(String host) throws EmptyHostNameException {
		this(host,3868);
	}
	/**
	 * Constructs a peer from a host name and port.
	 * The address is set to the specified host-name. The IP-address of the
	 * host is not immediately resolved. The port is set to the specified
	 * port, and the secure setting is off.
	 * @param host The host-name of the peer (preferably fully-qualified)
	 * @param port The port of the peer.
	 */
	public Peer(String host, int port) throws EmptyHostNameException {
		this(host,port,TransportProtocol.tcp);
	}
	/**
	 * Constructs a peer from a host name, port and transport-protocol.
	 * The address is set to the specified host-name. The IP-address of the
	 * host is not immediately resolved. The port is set to the specified
	 * port, and the secure setting is off.
	 * @param host The host-name of the peer (preferably fully-qualified)
	 * @param port The port of the peer.
	 * @param transport_protocol TCP or SCTP
	 * @since 0.9.5
	 */
	public Peer(String host, int port, TransportProtocol transport_protocol) throws EmptyHostNameException {
		if(host.length()==0)
			throw new EmptyHostNameException();
		this.host = new String(host);
		this.port = port;
		this.secure = false;
		this.transport_protocol = transport_protocol;
	}
	/**
	 * Constructs a peer from a socket address.
	 * The address and port is set to the specifed socket address.
	 * The secure setting is off.
	 * @param address The socket address of the peer.
	 */
	public Peer(InetSocketAddress address) {
		this.host = address.getAddress().getHostAddress();
		this.port = address.getPort();
		this.secure = false;
		this.transport_protocol = TransportProtocol.tcp;
	}
	/**
	 * Constructs a peer from a URI.
	 * Only URIs as specified in
	 * RFC3855 section 4.3 are supported. Please note that the [transport]
	 * and [protocol] part are not supported and ignored because the URI
	 * class has problems with parsing them.
	 * @param uri The Diameter URI
	 */
	public Peer(URI uri) throws UnsupportedURIException {
		if(uri.getScheme()!=null && !uri.getScheme().equals("aaa") && !uri.getScheme().equals("aaas"))
			throw new UnsupportedURIException("Only aaa: schemes are supported");
		if(uri.getUserInfo()!=null)
			throw new UnsupportedURIException("userinfo not supported in Diameter URIs");
		if(uri.getPath()!=null && !uri.getPath().equals(""))
			throw new UnsupportedURIException("path not supported in Diameter URIs");
		host = uri.getHost();
		port = uri.getPort();
		if(port==-1) port=3868;
		secure = uri.getScheme().equals("aaas");
	}
	/** Copy constructor (deep copy)*/
	public Peer(Peer p) {
		this.host = new String(p.host);
		this.port = p.port;
		this.secure = p.secure;
		if(p.capabilities!=null)
			this.capabilities = new Capability(p.capabilities);
		p.transport_protocol = p.transport_protocol;

	}
	/**Capabilities of this peer*/
	public Capability capabilities;
	
	/**
	 * Returns the Diameter URI of the peer
	 * @return the Diameter URI of the peer, eg. "aaa://somehost.example.net"
	 */
	public URI uri() {
		try {
			return new URI(secure?"aaas":"aaa", null, host, port, null,null,null);
		} catch(java.net.URISyntaxException e) {}
		return null;
	}
	
	/**
	 * Creates a peer from a Diameter URI string.
	 * @param s The Diameter URI string, eg. "aaa://somehost.example.net"
	 */
	public static Peer fromURIString(String s) throws UnsupportedURIException {
		//The URI class has problems with DiameterURIs with transport or protocol parts
		//We have to parse them ourselves
		int i = s.indexOf(';');
		String extra_stuff = null;
		if(i!=-1) {
			extra_stuff = s.substring(i+1);
			s = s.substring(0,i);
		}
		URI uri;
		try {
			uri = new URI(s);
		} catch(java.net.URISyntaxException e) {
			throw new UnsupportedURIException(e);
		}
		Peer p = new Peer(uri);
		if(extra_stuff!=null) {
			StringTokenizer st1 = new StringTokenizer(extra_stuff,";");
			while(st1.hasMoreTokens()) {
				String part = st1.nextToken();
				StringTokenizer st2 = new StringTokenizer(part,"=");
				if(!st2.hasMoreTokens())
					continue;
				String element_name = st2.nextToken();
				if(!element_name.equals("transport"))
					continue;
				if(!st2.hasMoreTokens())
					continue;
				String element_value = st2.nextToken();
				if(element_value.equals("sctp"))
					p.transport_protocol = TransportProtocol.sctp;
				else if(element_value.equals("tcp"))
					p.transport_protocol = TransportProtocol.tcp;
				else
					throw new UnsupportedURIException("Unknown transport-protocol: "+ element_value);
			}
		}
		return p;
	}
	
	public String host() {
		return host;
	}
	public void host(String host) {
		this.host = host;
	}
	public int port() {
		return port;
	}
	public void port(int port) {
		this.port = port;
	}
	public boolean secure() {
		return secure;
	}
	public void secure(boolean secure) {
		this.secure = secure;
	}
	/**
	 * @since 0.9.5
	 */
	public TransportProtocol transportProtocol() {
		return transport_protocol;
	}
	/**
	 * @since 0.9.5
	 */
	public void transportProtocol(TransportProtocol transport_protocol) {
		this.transport_protocol = transport_protocol;
	}
	
	public String toString() {
		return (secure?"aaas":"aaa")
		     + "://"
		     + host
		     + ":"
		     + (new Integer(port)).toString()
		     + (transport_protocol==TransportProtocol.tcp?"":";transport=sctp")
		     ;
	}
	
	public int hashCode() {
		return port + host.hashCode();
	}
	
	public boolean equals(Object o) {
		Peer p=(Peer)o;
		return port==p.port &&
		       host.equals(p.host);
	}
}
