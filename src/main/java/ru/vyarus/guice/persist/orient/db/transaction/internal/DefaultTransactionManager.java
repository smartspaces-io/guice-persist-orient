package ru.vyarus.guice.persist.orient.db.transaction.internal;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.orientechnologies.orient.core.tx.OTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vyarus.guice.persist.orient.db.PoolManager;
import ru.vyarus.guice.persist.orient.db.transaction.TransactionManager;
import ru.vyarus.guice.persist.orient.db.transaction.TxConfig;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class DefaultTransactionManager implements TransactionManager {
    private final Logger logger = LoggerFactory.getLogger(DefaultTransactionManager.class);

    private Set<PoolManager> pools;
    private ThreadLocal<TxConfig> transaction = new ThreadLocal<TxConfig>();
    private TxConfig defaultConfig;


    @Inject
    public DefaultTransactionManager(final Set<PoolManager> pools,
                                     final @Named("orient.txconfig") TxConfig defaultConfig) {
        this.pools = pools;
        this.defaultConfig = defaultConfig;
    }

    @Override
    public void begin() {
        begin(null);
    }

    @Override
    public void begin(final TxConfig config) {
        if (transaction.get() != null) {
            // transaction already in progress
            return;
        }
        transaction.set(Objects.firstNonNull(config, defaultConfig));
        logger.trace("Transaction opened: {}", transaction.get());
    }

    @Override
    public void end() {
        Preconditions.checkState(isTransactionActive(), "No active transaction found to close");
        logger.trace("Committing transaction");
        try {
            RuntimeException commitFailReason = null;
            // each pool maintains ots own transaction. we have to commit each of them
            // and only after that throw error to notify that something was failed
            // (there is no way to synchronize transaction between pools, but usually its not required because only
            // one pool used most of the time, otherwise plan transaction architecture accordingly - make transactions more granular)
            for (PoolManager<?> pool : pools) {
                try {
                    pool.commit();
                } catch (RuntimeException th) {
                    logger.debug("Pool " + pool.getType() + " commit failed. Exception will be propagated");
                    logger.trace("Pool " + pool.getType() + " commit fail cause", th);
                    if (commitFailReason != null) {
                        logger.error("More than one pool commit fail. Previous fail will not be propagated", commitFailReason);
                    }
                    commitFailReason = th;
                }
            }
            if (commitFailReason != null) {
                rollback();
                throw commitFailReason;
            }
        } finally {
            logger.trace("Transaction committed");
            transaction.remove();
        }
    }

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public void rollback(Throwable ex) {
        Preconditions.checkState(isTransactionActive(), "Call to rollback, when no active transaction");
        logger.trace("Rollback transaction: {}", transaction.get());
        if (ex != null) {
            logger.trace("Exception caused rollback:", ex);
            if (canRecover(transaction.get(), ex)) {
                logger.debug("Transaction recovered from exception: {}", ex.getClass());
                end();
                return;
            }
        }


        // performing actual rollback
        try {
            // it's very unlikely for rollback to fail because of db, but may happen because of db impl
            for (PoolManager<?> pool : pools) {
                try {
                    pool.rollback();
                } catch (Throwable th) {
                    if (ex != null) {
                        // logging second time but with higher level, it may be important
                        logger.warn("Exception caused transaction rollback:", ex);
                    }
                    logger.error("Failed to rollback pool " + pool.getType() + " transaction", th);
                }
            }
        } finally {
            logger.trace("Transaction rolled back");
            transaction.remove();
        }
    }

    @Override
    public boolean isTransactionActive() {
        return transaction.get() != null;
    }

    @Override
    public OTransaction.TXTYPE getActiveTransactionType() {
        return isTransactionActive() ? transaction.get().getTxtype() : null;
    }

    /**
     * Returns True if rollback DID NOT HAPPEN (i.e. if commit should continue).
     *
     * @param config transaction configuration
     * @param e      The exception to test for rollback
     */
    private boolean canRecover(final TxConfig config, Throwable e) {
        boolean commit = config.getRollbackOn().size() > 0;

        //check rollback clauses
        for (Class<? extends Exception> rollBackOn : config.getRollbackOn()) {

            //if one matched, try to perform a rollback
            if (rollBackOn.isInstance(e)) {
                commit = false;
                break;
            }
        }

        //check ignore clauses (supercedes rollback clause)
        for (Class<? extends Exception> exceptOn : config.getIgnore()) {
            //An exception to the rollback clause was found, DON'T rollback
            // (i.e. commit and throw anyway)
            if (exceptOn.isInstance(e)) {
                commit = true;
                break;
            }
        }
        return commit;
    }
}