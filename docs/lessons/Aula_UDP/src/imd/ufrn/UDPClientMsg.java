package imd.ufrn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

class UDPClientMsg {

	public UDPClientMsg() {
		System.out.println("UDP Client Started");
		Scanner scanner = new Scanner(System.in);
		try {
			DatagramSocket clientSocket = new DatagramSocket();
			InetAddress inetAddress = InetAddress.getByName("localhost");
			byte[] sendMessage;
			Message msg = new Message(1,"teste de valor 1");
			
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(msg);
			byte[] data = outputStream.toByteArray();
			DatagramPacket sendPacket = new DatagramPacket(
						data, data.length,	inetAddress, 9003);
			clientSocket.send(sendPacket);
			System.out.println("UDP Client Terminating ");
			clientSocket.close();	
			
			} catch (IOException ex) {
				ex.printStackTrace();
			} 
		
	}

	public static void main(String args[]) {
		new UDPClientMsg();
	}
}