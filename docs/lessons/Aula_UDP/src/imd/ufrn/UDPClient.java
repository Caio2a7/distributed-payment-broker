package imd.ufrn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

class UDPClient {

	public UDPClient() {
		System.out.println("UDP Client Started");
		Scanner scanner = new Scanner(System.in);
		try {
			DatagramSocket clientSocket = new DatagramSocket();
			InetAddress inetAddress = InetAddress.getByName("localhost");
			byte[] sendMessage;
			while (true) {
				System.out.print("Enter a message: ");
				String message = scanner.nextLine();
				if ("quit".equalsIgnoreCase(message)) {
					break;
				}
				sendMessage = message.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(
						sendMessage, sendMessage.length,
						inetAddress, 9003);
				clientSocket.send(sendPacket);
			}
			clientSocket.close();
		} catch (IOException ex) {
		}
		System.out.println("UDP Client Terminating ");
	}

	public static void main(String args[]) {
		new UDPClient();
	}
}