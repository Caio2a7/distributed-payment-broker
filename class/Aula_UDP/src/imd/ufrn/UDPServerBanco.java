package imd.ufrn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServerBanco {
	private Banco banco;
	ExecutorService poolvthreads = Executors.newVirtualThreadPerTaskExecutor();

	public UDPServerBanco(String port) {
		banco = new Banco();
		System.out.println("UDP Server Bank started");
		try {
			DatagramSocket serversocket = new DatagramSocket(Integer.parseInt(port));
			while (true) {
				byte[] receivemessage = new byte[1024];
				DatagramPacket receivepacket = new DatagramPacket(receivemessage, receivemessage.length);
				serversocket.receive(receivepacket);
				String message = new String(receivepacket.getData(), 0, receivepacket.getLength());
				//poolvthreads.submit(() -> {
					processarMensagem(message, receivepacket, serversocket);
				//});

			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException nfe) {
			System.out.println("Erro ao converter numero: " + nfe.getMessage());

		} catch (Exception e) {
			System.out.println("Erro inesperado: " + e.getMessage());
		}
		System.out.println("UDP Bank server terminating");
	}

	private void processarMensagem(String message, DatagramPacket receivepacket, DatagramSocket serversocket) {
		String operacao = null;
		int conta = 0;
		int valor = 0;
		String resultadoOp = message;
		try {
			StringTokenizer tokenizer = new StringTokenizer(message, ";");
			while (tokenizer.hasMoreElements()) {
				operacao = tokenizer.nextToken();
				conta = Integer.parseInt(tokenizer.nextToken());
				valor = Integer.parseInt(tokenizer.nextToken().trim());
			}
			switch (operacao) {
				case "criar":
					banco.addConta(conta);
					break;
				case "depositar":
					banco.depositar(conta, valor);
					break;
				case "saldo":
					resultadoOp = "R$" + banco.saldo(conta);
					break;
			}
			System.out.println(
					"Operacao realizada:" + operacao + "-" + banco.saldo(conta) + "-" + receivepacket.getAddress());
			String reply = "Confirmo Recebimento de:" + resultadoOp;
			byte[] replymsg = reply.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(replymsg, replymsg.length,
					receivepacket.getAddress(), receivepacket.getPort());
			serversocket.send(sendPacket);
		} catch (IOException e) {
				e.printStackTrace();
		} catch (NumberFormatException nfe) {
			System.out.println("Erro ao converter numero: " + nfe.getMessage());
		}
	}

	public static void main(String[] args) {
		new UDPServerBanco("9004");
	}
}
