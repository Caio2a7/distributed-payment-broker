package br.ufrn.dpb.gateway.balance;

import br.ufrn.dpb.gateway.channel.SingleSocketChannel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public SingleSocketChannel choose(List<SingleSocketChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalStateException("No available channels for routing");
        }
        int i = Math.abs(index.getAndIncrement() % channels.size());
        return channels.get(i);
    }
}
