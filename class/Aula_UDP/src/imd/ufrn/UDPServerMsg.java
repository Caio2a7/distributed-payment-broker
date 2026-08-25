package imd.ufrn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServerMsg {
	public UDPServerMsg() {
		System.out.println("UDP Server Started");
		try {
			DatagramSocket serverSocket = new DatagramSocket(9003);
			while (true) {
				byte[] receiveMessage = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
				serverSocket.receive(receivePacket);
								
				byte[] data = receivePacket.getData();
				ByteArrayInputStream in = new ByteArrayInputStream(data);
				ObjectInputStream is = new ObjectInputStream(in);
				try {
					Message msg = (Message) is.readObject();
					System.out.println("Msg recebida com tipo de operação = "+msg.getType()+", e conteudo:"+msg.getContent());
				} catch (ClassNotFoundException e) {
				e.printStackTrace();
				}
				
				
			}
		}catch (IOException e) {
				e.printStackTrace();
				System.out.println("UDP Server Terminating");		
		}
	}
	public static void main(String[] args) { 
			new UDPServerMsg();    
		}
}
