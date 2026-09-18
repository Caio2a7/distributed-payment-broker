package br.ufrn.dpb.gateway.balance;

import br.ufrn.dpb.gateway.channel.SingleSocketChannel;
import java.util.List;

public interface LoadBalancer {
    SingleSocketChannel choose(List<SingleSocketChannel> channels);
}
