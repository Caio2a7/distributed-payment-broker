package br.ufrn.dpb.gateway.registry;

import br.ufrn.dpb.gateway.channel.SingleSocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class ServiceRegistry {
    private static final Logger LOGGER = Logger.getLogger(ServiceRegistry.class.getName());
    private final Map<NodeType, List<SingleSocketChannel>> registry = new ConcurrentHashMap<>();

    public ServiceRegistry() {
        for (NodeType type : NodeType.values()) {
            registry.put(type, new CopyOnWriteArrayList<>());
        }
    }

    public void register(NodeType type, SingleSocketChannel channel) {
        if (type == NodeType.LEDGER) {
            List<SingleSocketChannel> existing = registry.get(type);
            for (SingleSocketChannel oldCh : existing) {
                try { oldCh.close(); } catch (Exception ignored) {}
            }
            existing.clear();
        } else if (type == NodeType.AUTHORIZER) {
            String remoteHost = channel.getSocket().getInetAddress().getHostAddress();
            registry.get(type).removeIf(oldCh -> {
                if (oldCh.getSocket().getInetAddress().getHostAddress().equals(remoteHost)) {
                    try { oldCh.close(); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            });
        }
        registry.get(type).add(channel);
        LOGGER.info(() -> "Node registered: [" + type + "] from " + channel.getSocket().getRemoteSocketAddress());
    }

    public void unregister(SingleSocketChannel channel, String cause) {
        for (Map.Entry<NodeType, List<SingleSocketChannel>> entry : registry.entrySet()) {
            if (entry.getValue().remove(channel)) {
                LOGGER.warning(() -> "Node unregistered: [" + entry.getKey() + "] from "
                        + channel.getSocket().getRemoteSocketAddress() + " | Cause: " + cause);
                try {
                    channel.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void unregister(SingleSocketChannel channel) {
        unregister(channel, "Connection closed / End of stream");
    }

    public List<SingleSocketChannel> getChannels(NodeType type) {
        List<SingleSocketChannel> list = registry.getOrDefault(type, List.of());
        list.removeIf(ch -> !ch.isConnected());
        return list;
    }

    public List<SingleSocketChannel> getAllChannels() {
        List<SingleSocketChannel> all = new ArrayList<>();
        for (List<SingleSocketChannel> list : registry.values()) {
            list.removeIf(ch -> !ch.isConnected());
            all.addAll(list);
        }
        return all;
    }
}
