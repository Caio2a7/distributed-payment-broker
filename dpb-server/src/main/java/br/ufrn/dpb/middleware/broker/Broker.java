package br.ufrn.dpb.middleware.broker;

import br.ufrn.dpb.middleware.invoker.Invoker;
import br.ufrn.dpb.middleware.transport.ServerRequestHandler;
import br.ufrn.dpb.middleware.transport.udp.UDPServer;


public class Broker {
    private final Invoker invoker = new Invoker();
    private ServerRequestHandler server;

    public void register(Object target){
        invoker.registerRoutes(target);
    }

    public void startUDP(int port) {
        this.server = new UDPServer(port, invoker);
        this.server.start();
    }
}
