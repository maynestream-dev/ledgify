package dev.maynestream.ledgify.ledger;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("bookkeeper")
public class BookkeeperConfiguration {

    private String zkServers;
    private int defaultEnsembleSize = 3;
    private int defaultWriteQuorumSize = 2;
    private int defaultAckQuorumSize = 2;
}
