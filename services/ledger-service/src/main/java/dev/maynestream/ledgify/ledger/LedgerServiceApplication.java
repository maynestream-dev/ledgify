package dev.maynestream.ledgify.ledger;

import dev.maynestream.ledgify.error.GrpcExceptionAdvice;
import dev.maynestream.ledgify.ledger.error.GrpcLedgerExceptionAdvice;
import org.apache.bookkeeper.client.BKException;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
@EnableConfigurationProperties
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }

    @Bean
    GrpcExceptionAdvice grpcExceptionAdvice() {
        return new GrpcExceptionAdvice();
    }

    @Bean
    GrpcLedgerExceptionAdvice grpcLedgerExceptionAdvice() {
        return new GrpcLedgerExceptionAdvice();
    }

    @Bean
    BookKeeper bkClient(BookkeeperConfiguration bookkeeperConfiguration) throws BKException, IOException, InterruptedException {
        final ClientConfiguration config = new ClientConfiguration().enableBookieHealthCheck();
        config.setZkServers(bookkeeperConfiguration.getZkServers());
        config.setMetadataServiceUri(bookkeeperConfiguration.getMetadataServiceUri());
        config.setAddEntryTimeout(2000);
        return BookKeeper.forConfig(config).build();
    }

    @Bean
    CuratorFramework curatorClient(BookkeeperConfiguration bookkeeperConfiguration) throws InterruptedException {
        final CuratorFramework curator = buildCurator(bookkeeperConfiguration);
        curator.start();
        curator.blockUntilConnected();
        return curator;
    }

    private static CuratorFramework buildCurator(final BookkeeperConfiguration bookkeeperConfiguration) {
        return CuratorFrameworkFactory.builder()
                                      // TODO this is fine for local
                                      .ensembleTracker(false)
                                      .ensembleProvider(new FixedEnsembleProvider(bookkeeperConfiguration.getZkServers(),
                                                                                  false))
                                      .sessionTimeoutMs(2000)
                                      .connectionTimeoutMs(10000)
                                      .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                                      .build();
    }
}

