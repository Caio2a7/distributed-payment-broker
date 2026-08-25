package ufrn.imd.br;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

public class TCPServerRaw {
	public static void main(String args[]) {
		System.out.println("TCP Server Bank Started");
		ServerSocket server = null;
		Banco banco = new Banco();
		ProcessPayload processplayload = new ProcessPayload(banco);
		try {
			server = new ServerSocket(8080,300);
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		while (true) {
			try {
				Socket conexao = server.accept();
				BufferedReader input = new BufferedReader(new InputStreamReader(conexao.getInputStream()));
				String msg = input.readLine(); 
				System.out.println("Operação recebida:"+msg);
				
				String reply =  processplayload.processData(msg,conexao.getInetAddress().toString());
				
				PrintWriter output = new PrintWriter(conexao.getOutputStream(), true);
				output.println("Server response: " + reply);
				output.flush();
				conexao.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
