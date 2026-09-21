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
        return registry.getOrDefault(type, List.of());
    }

    public List<SingleSocketChannel> getAllChannels() {
        List<SingleSocketChannel> all = new ArrayList<>();
        for (List<SingleSocketChannel> list : registry.values()) {
            all.addAll(list);
        }
        return all;
    }
}
