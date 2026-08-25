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
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServerRawNThreadPlatform {
    private static final int THREAD_POOL_SIZE = 300; 
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public static void main(String args[]) {
        System.out.println("TCP Server Bank Started");
        Banco banco = new Banco();
        ProcessPayload processPayload = new ProcessPayload(banco);

        try {
        	ServerSocket server = new ServerSocket(8080,1000);
            while (true) {
                try {
                    Socket conexao = server.accept();
                    threadPool.execute(() -> handleClient(conexao, processPayload));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleClient(Socket conexao, ProcessPayload processPayload) {
        try (conexao;
             BufferedReader input = new BufferedReader(new InputStreamReader(conexao.getInputStream()));
             PrintWriter output = new PrintWriter(conexao.getOutputStream(), true)) {
            
            String msg = input.readLine();
            System.out.println("Operação recebida de " + conexao.getInetAddress() + ": " + msg);
            
            String reply = processPayload.processData(msg, conexao.getInetAddress().toString());
            output.println("Server response: " + reply);
            
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }
}