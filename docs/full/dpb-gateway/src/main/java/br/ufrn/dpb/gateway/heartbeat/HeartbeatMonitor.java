package br.ufrn.dpb.gateway.heartbeat;

import br.ufrn.dpb.gateway.channel.SingleSocketChannel;
import br.ufrn.dpb.gateway.registry.ServiceRegistry;
import br.ufrn.dpb.middleware.protocol.Message;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class HeartbeatMonitor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(HeartbeatMonitor.class.getName());
    private final ServiceRegistry registry;
    private final int intervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread monitorThread;

    public HeartbeatMonitor(ServiceRegistry registry, int intervalMs) {
        this.registry = registry;
        this.intervalMs = intervalMs;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            monitorThread = new Thread(this, "HeartbeatMonitorThread");
            monitorThread.setDaemon(true);
            monitorThread.start();
            LOGGER.info(() -> "HeartbeatMonitor started (interval: " + intervalMs + "ms)");
        }
    }

    public void stop() {
        running.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Thread.sleep(intervalMs);
                List<SingleSocketChannel> channels = registry.getAllChannels();
                Message ping = new Message(Message.Type.HEARTBEAT, 0, new byte[0]);

                for (SingleSocketChannel channel : channels) {
                    try {
                        channel.writeMessage(ping);
                    } catch (IOException e) {
                        registry.unregister(channel, "Heartbeat ping failed / Socket timeout (" + e.getMessage() + ")");
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                LOGGER.warning(() -> "Error in heartbeat loop: " + e.getMessage());
            }
        }
    }
}
