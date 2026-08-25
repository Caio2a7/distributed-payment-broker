package imd.ufrn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {
	public UDPServer() {
		System.out.println("UDP Server Started");
		try {
			DatagramSocket serverSocket = new DatagramSocket(9003);
			while (true) {
				byte[] receiveMessage = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
				serverSocket.receive(receivePacket);
				String message = new String(receivePacket.getData());
				System.out.println("Received from client: " + message+ "\nFrom: " + receivePacket.getAddress());
				
			}
		}catch (IOException e) {
				e.printStackTrace();
				System.out.println("UDP Server Terminating");		
		}
	}
	public static void main(String[] args) { 
			new UDPServer();    
		}
}
