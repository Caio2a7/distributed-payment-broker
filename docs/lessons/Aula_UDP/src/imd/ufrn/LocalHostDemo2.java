package imd.ufrn;

import java.net.*;
public class LocalHostDemo2{ 
	public static void main(String args[]) {
		if (args.length != 1) {
			System.err.println ("Syntax - NetworkResolverDemo host");
            System.exit(0);
			}
		System.out.println ("Resolving " + args[0]);
		try {
			InetAddress addr = InetAddress.getByName  ( args[0] );
			System.out.println ("IP address : " +  addr.getHostAddress() );
			System.out.println ("Hostname : " +   addr.getHostName() );
      	}catch (UnknownHostException uhe) {
			System.out.println ("Error - unable to resolve localhost");
		}
	}
}
     