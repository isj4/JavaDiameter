package dk.i1.diameter.node;
import dk.i1.sctp.SCTPSocket;
import dk.i1.sctp.AssociationId;

/**
Peer authentication information (SCTP).
Instances of this class is used for passing information to {@link NodeValidator}.
@since 0.9.5
*/
public class RelevantSCTPAuthInfo {
	public SCTPSocket sctp_socket;
	public AssociationId assoc_id;
	RelevantSCTPAuthInfo(SCTPSocket sctp_socket, AssociationId assoc_id) {
		this.sctp_socket = sctp_socket;
		this.assoc_id = assoc_id;
	}
}
