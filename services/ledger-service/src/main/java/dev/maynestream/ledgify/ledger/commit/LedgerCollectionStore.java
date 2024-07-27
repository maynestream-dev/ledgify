package dev.maynestream.ledgify.ledger.commit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.util.Objects;

public class LedgerCollectionStore {
    private static final String LEDGERS_COLLECTION_PATH_FORMAT = "/ledgers-collection/%s";

    private final CuratorFramework curator;
    private final String collectionPath;

    public LedgerCollectionStore(final CuratorFramework curator, final String ledgerPath) {
        this.curator = curator;
        this.collectionPath = buildLedgerPath(ledgerPath);
    }

    void create(final Stat stat, final LedgerCollection ledgers) throws Exception {
        curator.create()
               .creatingParentContainersIfNeeded()
               .storingStatIn(stat)
               .forPath(collectionPath, ledgers.toBytes());
    }

    void update(final Stat stat, final LedgerCollection ledgers) throws Exception {
        curator.setData()
               .withVersion(stat.getVersion())
               .forPath(collectionPath, ledgers.toBytes());
    }

    LedgerCollection load(final Stat stat) throws Exception {
        try {
            byte[] ledgerListBytes = curator.getData().storingStatIn(stat).forPath(collectionPath);
            return LedgerCollection.fromBytes(ledgerListBytes);
        } catch (KeeperException.NoNodeException nne) {
            // ledger collection doesn't yet exist
            return new LedgerCollection();
        }
    }

    LedgerCollection load() throws Exception {
        byte[] ledgerListBytes = curator.getData().forPath(collectionPath);
        return LedgerCollection.fromBytes(ledgerListBytes);
    }

    private static String buildLedgerPath(final String ledgerPath) {
        Objects.requireNonNull(ledgerPath, "ledgerPath cannot be null");
        return LEDGERS_COLLECTION_PATH_FORMAT.formatted(ledgerPath);
    }
}
