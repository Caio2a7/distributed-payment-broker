package br.imd.ufrn;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class WebServer {

	public WebServer() {

		System.out.println("Meu Webserver Started");

		try (ServerSocket serverSocket = new ServerSocket(8080, 300)) {

			while (true) {

				System.out.println("Aguardando requisições do cliente"+serverSocket.getInetAddress());

				Socket remote = serverSocket.accept();

				System.out.println("Conexão estabelecida com " + remote.getRemoteSocketAddress());

				new Thread(new ClientHandler(remote)).start();

			}

		} catch (IOException ex) {

			ex.printStackTrace();

		}

	}

	public static void main(String args[]) {

		new WebServer();

	}
}
