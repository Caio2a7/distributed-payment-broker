package br.ufrn.dpb.gateway;

import br.ufrn.dpb.gateway.balance.LoadBalancer;
import br.ufrn.dpb.gateway.balance.RoundRobinLoadBalancer;
import br.ufrn.dpb.gateway.heartbeat.HeartbeatMonitor;
import br.ufrn.dpb.gateway.registry.ServiceRegistry;
import br.ufrn.dpb.gateway.server.GatewayGrpcServer;
import br.ufrn.dpb.gateway.server.GatewayHttpServer;
import br.ufrn.dpb.gateway.server.GatewayTcpServer;
import br.ufrn.dpb.gateway.server.GatewayUdpServer;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        int udpPort = 9090;
        int tcpPort = 9090;
        int httpPort = 8080;
        int grpcPort = 50051;
        int heartbeatIntervalMs = 3000;

        System.setProperty("java.util.logging.SimpleFormatter.format",
            "%1$tT.%1$tL [\u001B[33mGATEWAY\u001B[0m] [\u001B[33m%4$s\u001B[0m] %3$s - %5$s%6$s%n");
        Logger.getLogger("").setLevel(Level.INFO);

        ServiceRegistry registry = new ServiceRegistry();
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

        HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor(registry, heartbeatIntervalMs);
        heartbeatMonitor.start();

        GatewayUdpServer udpServer = new GatewayUdpServer(udpPort, registry, loadBalancer);
        udpServer.start();

        GatewayTcpServer tcpServer = new GatewayTcpServer(tcpPort, registry, loadBalancer, heartbeatIntervalMs);
        tcpServer.start();

        GatewayHttpServer httpServer = new GatewayHttpServer(httpPort, registry, loadBalancer);
        httpServer.start();

        GatewayGrpcServer grpcServer = new GatewayGrpcServer(grpcPort, registry, loadBalancer);
        try {
            grpcServer.start();
        } catch (Exception e) {
            Logger.getLogger(Main.class.getName()).severe(() -> "Failed to start gRPC server: " + e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            udpServer.stop();
            tcpServer.stop();
            httpServer.stop();
            grpcServer.stop();
            heartbeatMonitor.stop();
        }));
    }
}
