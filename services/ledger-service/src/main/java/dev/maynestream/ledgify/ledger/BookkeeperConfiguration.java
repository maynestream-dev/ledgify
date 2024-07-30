package dev.maynestream.ledgify.ledger;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Data
@Component
@Profile("!test")
@ConfigurationProperties("ledger.bookkeeper")
public class BookkeeperConfiguration {

    @NotBlank
    private String zkServers;

    @NotBlank
    private String metadataServiceUri;

    @Min(1)
    private int defaultEnsembleSize = 3;

    @Min(1)
    private int defaultWriteQuorumSize = 2;

    @Min(1)
    private int defaultAckQuorumSize = 2;
}
