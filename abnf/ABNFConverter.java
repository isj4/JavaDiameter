import java.io.*;
import java.util.regex.*;
import java.util.*;

/**
ABNFConverter - convert RFC-style ABNF to java class.
This program takes as input (input redirect or file argument) something like this:
<pre>
       <CER> ::= < Diameter Header: 257, REQ >
                { Origin-Host }
                { Origin-Realm }
             1* { Host-IP-Address }
                { Vendor-Id }
                { Product-Name }
                [ Origin-State-Id ]
              * [ Supported-Vendor-Id ]
              * [ Auth-Application-Id ]
              * [ Inband-Security-Id ]
              * [ Acct-Application-Id ]
              * [ Vendor-Specific-Application-Id ]
                [ Firmware-Revision ]
              * [ AVP ]
</pre>
This program then parses the ABNF and outputs a class that can decode/encode
messages according to the ABNF. Something like this is generated:
<pre>
import dk.i1.diameter.*;
import java.util.*;

public class CER {
        public AVP            origin_host;
        public AVP            origin_realm;
        public ArrayList<AVP> host_ip_address;
        public AVP            vendor_id;
...
        public static CER fromMessage(Message msg) {
...
        public Message toMessage() {
...
</pre>
The generated class also contains
Utils.ABNFComponent array that should be used to validate ingoing and
outgoing packets.
<p>
Todo: generate native types instead of generic 'AVP' (requires dictionary);
should check ABNF; should make it easy to set origin-host/realm; etc.
*/
public class ABNFConverter {
	private static class AVP {
		public boolean fixed_position;
		public boolean multiple;
		public int min_occurrences;
		public int max_occurrences;
		public String name;
		public String java_name;
		public String constant_name;
	};
	
	public static final void main(String args[]) throws Exception {
		InputStream is;
		if(args.length==0)
			is = System.in;
		else {
			is = new FileInputStream(args[0]);
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		
		String message_name=null;
		
		for(;;) {
			String s = br.readLine();
			if(s.indexOf("::=")==-1)
				continue;
			//parse line like:
			//      <CER> ::= < Diameter Header: 257, REQ >
			Matcher m = Pattern.compile("([^<]*<)([^>]+).*").matcher(s);
			m.matches();
			
			message_name = m.group(2);
			break;
		}
		
		System.out.println("import dk.i1.diameter.*;");
		System.out.println("import java.util.*;");
		System.out.println("");
		System.out.println("public class "+message_name+" {");
		boolean arbitrary_avps_allowed=false;
		
		ArrayList<AVP> avps = new ArrayList<AVP>();
		for(;;) {
			String s = br.readLine();
			if(s==null) break;
			
			Matcher m = Pattern.compile("( *)([0-9]*)(\\*)?([0-9]*) *([{\\[<]) *([a-zA-Z-]+) *([}\\]>])").matcher(s);
			if(m.matches()) {
				AVP avp = new AVP();
				avp.fixed_position = m.group(5).equals("<");
				avp.multiple = m.group(3)!=null;
				if(avp.multiple) {
					if(m.group(2).equals(""))
						avp.min_occurrences = 0;
					else
						avp.min_occurrences = Integer.valueOf(m.group(2));
					if(m.group(4).equals(""))
						avp.max_occurrences = -1;
					else
						avp.max_occurrences = Integer.valueOf(m.group(4));
				} else {
					if(m.group(5).equals("<") || m.group(5).equals("{")) {
						avp.min_occurrences = 1;
						avp.max_occurrences = 1;
					} else {
						avp.min_occurrences = 0;
						avp.max_occurrences = 1;
					}
				}
				avp.name = m.group(6);
				
				if(!avp.name.equals("AVP")) {
					avp.java_name = avp.name.toLowerCase().replace('-','_');
					avp.constant_name = "DI_"+avp.name.toUpperCase().replace('-','_');
				} else
					arbitrary_avps_allowed = true;
				avps.add(avp);
			}
		}
		
		for(AVP avp:avps) {
			String l="\t";
			l+="public ";
			if(avp.multiple)
				l+="ArrayList<AVP> ";
			else
				l+="AVP            ";
			if(avp.java_name!=null)
				l+=avp.java_name;
			else
				l+="avp";
			l+=";";
			System.out.println(l);
		}
		
		System.out.println("\t");
		
		//Generate constructor
		System.out.println("\tprivate "+message_name+"() {");
		System.out.println("\t}");
		
		System.out.println("\t");
		
		//Generate ABNF records
		System.out.println("\tpublic static final Utils.ABNFComponent abnf_"+message_name.toLowerCase()+"[] = {");
		for(AVP avp:avps) {
			String l="\t\t";
			l+="new Utils.ABNFComponent(";
			if(avp.fixed_position)
				l+="true,  ";
			else
				l+="false, ";
			l+=Integer.toString(avp.min_occurrences)+", ";
			l+=Integer.toString(avp.max_occurrences)+", ";
			if(avp.constant_name!=null)
				l+="ProtocolConstants."+avp.constant_name;
			else
				l+="-1";
			l+="),";
			System.out.println(l);
				
		}
		System.out.println("\t};");
		
		System.out.println("\t");
		
		//Generate fromMessage()
		System.out.println("\tpublic static "+message_name+" fromMessage(Message msg) {");
		System.out.println("\t\t"+message_name+" result = new "+message_name+";");
		if(arbitrary_avps_allowed)
			System.out.println("\t\tSet<AVP> processed_avps = new HashSet<AVP>();");
		for(AVP avp:avps) {
			if(avp.java_name==null) continue;
			if(avp.multiple) {
				System.out.println("\t\tresult."+avp.java_name+" = new ArrayList<AVP>();");
				System.out.println("\t\tfor(AVP avp : msg.subset(ProtocolConstants."+avp.constant_name+")) {");
				System.out.println("\t\t\tresult."+avp.java_name+".add(new AVP(avp));");
				if(arbitrary_avps_allowed)
					System.out.println("\t\t\tprocessed_avps.add(avp);");
				System.out.println("\t\t}");
			} else {
				System.out.println("\t\tresult."+avp.java_name+" = msg.find("+avp.constant_name+");");
			}
		}
		if(arbitrary_avps_allowed) {
			System.out.println("\t\tresult.avps = new ArrayList<AVP>();");
			System.out.println("\t\tfor(AVP avp : msg.avps()) {");
			System.out.println("\t\t\tif(!processed_avps.contains(avp))");
			System.out.println("\t\t\t\t result.avps.add(avp);");
			System.out.println("\t\t}");
		}
		System.out.println("\t\t");
		System.out.println("\t\treturn result;");
		System.out.println("\t}");
		
		//Generate toMessage()
		System.out.println("\tpublic Message toMessage() {");
		System.out.println("\t\tMessage msg = new Message();");
		for(AVP avp:avps) {
			if(avp.multiple) {
				if(avp.java_name==null)
					System.out.println("\t\tfor(AVP avp : avps) {");
				else
					System.out.println("\t\tfor(AVP avp : "+avp.java_name+") {");
				System.out.println("\t\t\tmsg.add(avp);");
				System.out.println("\t\t}");
			} else {
				if(avp.min_occurrences==1)
					System.out.println("\t\tmsg.add("+avp.java_name+");");
				else {
					System.out.println("\t\tif("+avp.java_name+"!=null)");
					System.out.println("\t\t\tmsg.add("+avp.java_name+");");
				}
			}
		}
		System.out.println("\t\t");
		System.out.println("\t\treturn msg;");
		System.out.println("\t}");
		
		System.out.println("}");
	}
}
