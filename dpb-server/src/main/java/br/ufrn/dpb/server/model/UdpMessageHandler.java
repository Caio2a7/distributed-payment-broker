package br.ufrn.dpb.server.model;

import java.net.InetAddress;

public interface UdpMessageHandler {
   byte[] onMessage(byte[] data, InetAddress senderAddress, int port); 
}
