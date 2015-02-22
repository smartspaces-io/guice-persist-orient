package ru.vyarus.guice.persist.orient.db.pool;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vyarus.guice.persist.orient.db.DbType;
import ru.vyarus.guice.persist.orient.db.transaction.TransactionManager;
import ru.vyarus.guice.persist.orient.db.user.UserManager;

/**
 * Document pool implementations.
 * Connection may be obtained (using provider) only inside unit of work (defined transaction).
 * Because of possible multi-transaction paradigm (default pools now use single transaction, but as before
 * pools implementation may be overridden to mimic legacy behaviour) inside single unit of work,
 * transaction manager always calls commit and rollback on all pools (no matter which one is really need it).
 * Implementation handle it by ignoring redundant calls: first commit or rollback finish pool transaction
 * and all other calls to commit or rollback simply ignored.
 *
 * @author Vyacheslav Rusakov
 * @since 24.07.2014
 */
public class DocumentPool implements PoolManager<ODatabaseDocumentTx> {
    private final Logger logger = LoggerFactory.getLogger(DocumentPool.class);

    private final TransactionManager transactionManager;
    private final UserManager userManager;
    private final ThreadLocal<ODatabaseDocumentTx> transaction = new ThreadLocal<ODatabaseDocumentTx>();
    private OPartitionedDatabasePoolFactory poolFactory;
    private String uri;

    @Inject
    public DocumentPool(final TransactionManager transactionManager, final UserManager userManager) {
        this.transactionManager = transactionManager;
        this.userManager = userManager;
    }

    @Override
    public void start(final String uri) {
        this.uri = uri;
        poolFactory = new OPartitionedDatabasePoolFactory();
        poolFactory.setMaxPoolSize(OGlobalConfiguration.DB_POOL_MAX.getValueAsInteger());
        // check database connection
        new ODatabaseDocumentTx(uri).open(userManager.getUser(), userManager.getPassword());
        logger.debug("Pool {} started for '{}'", getType(), uri);
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment")
    public void stop() {
        if (poolFactory != null) {
            poolFactory.close();
            poolFactory = null;
            logger.debug("Pool {} closed for '{}'", getType(), uri);
            uri = null;
        }
    }

    @Override
    public void commit() {
        final ODatabaseDocumentTx db = transaction.get();
        if (db == null) {
            // pool not participate in current transaction
            return;
        }
        // connection was closed manually, no need for rollback
        if (db.isClosed()) {
            transaction.remove();
            checkOpened(db);
        }
        // may not cause actual commit/close because force parameter not used
        // in case of commit exception, transaction manager must perform rollback
        // (and close will take effect in rollback)
        db.commit();
        db.close();
        transaction.remove();
        logger.trace("Pool {} commit successful", getType());
    }

    @Override
    public void rollback() {
        final ODatabaseDocumentTx db = transaction.get();
        if (db == null) {
            // pool not participate in current transaction or already committed (may happen if one other pool's
            // transaction fail: in this case all other transactions will be committed and after that
            // transactional manager call rollback, which will affect only failed pool and others will simply ignore it)
            return;
        }
        try {
            // may not cause actual rollback immediately because force not used
            checkOpened(db).rollback();
            logger.trace("Pool {} rollback successful", getType());
        } finally {
            if (!db.isClosed()) {
                try {
                    // release connection back to pool in any case
                    db.close();
                } catch (Throwable ignored) {
                    logger.trace(String.format("Pool %s failed to close database", getType()), ignored);
                }
            }
            transaction.remove();
        }
    }

    @Override
    public ODatabaseDocumentTx get() {
        // lazy get: pool transaction will start not together with TransactionManager one, but as soon as
        // connection requested to avoid using connections of not used pools
        Preconditions.checkNotNull(poolFactory, String.format("Pool %s not initialized", getType()));
        if (transaction.get() == null) {
            Preconditions.checkState(transactionManager.isTransactionActive(), String.format(
                    "Can't obtain connection from pool %s: no transaction defined.", getType()));

            final ODatabaseDocumentTx db = checkAndRecoverConnection(
                    poolFactory.get(uri, userManager.getUser(), userManager.getPassword()).acquire());

            db.begin(transactionManager.getActiveTransactionType());
            transaction.set(db);
            logger.trace("Pool {} transaction started", getType());
        }
        return checkOpened(transaction.get());
    }

    /**
     * To early catch inconsistency errors it's better to check here (should reduce scope to search for problem).
     * It's so easy to call close directly on connection, but it shouldn't be done manually: either use unit of work
     * or completely manage connection yourself.
     *
     * @param db database connection instance
     * @return connection instance if its opened, otherwise error thrown
     */
    private ODatabaseDocumentTx checkOpened(final ODatabaseDocumentTx db) {
        Preconditions.checkState(!db.isClosed(), String.format(
                "Inconsistent %s pool state: thread-bound database closed! "
                        + "This may happen if close/commit/rollback was called directly on "
                        + "database connection object, which is not allowed (if you need full control "
                        + "on connection use manual setup and not pool managed connection)", getType()));
        return db;
    }

    /**
     * Its definitely not normal that pool returns closed connections, but possible if used improperly.
     * If connection closed, trying to recover by restarting entire pool.
     *
     * @param db connection to check
     * @return connection itself or new valid connection
     */
    private ODatabaseDocumentTx checkAndRecoverConnection(final ODatabaseDocumentTx db) {
        ODatabaseDocumentTx res = db;
        if (db.isClosed()) {
            logger.warn("ATTENTION: Pool {} return closed connection, restarting pool. "
                    + "This is NOT normal situation: in spite of the fact that your logic will perform "
                    + "correctly, you loose predefined connections which may be very harmful for "
                    + "performance (especially if problem appear often). Most likely reason is "
                    + "closing underlying: e.g. connection.commit().close() or "
                    + "connection.getUnderlying().close(). Check your code: you should not call "
                    + "begin/commit/rollback/close (construct your own connection if you need "
                    + "full control, otherwise trust to transaction manager).", getType());
            final String localUri = uri;
            stop();
            start(localUri);
            poolFactory.get(uri, userManager.getUser(), userManager.getPassword()).close();
            res = poolFactory.get(uri, userManager.getUser(), userManager.getPassword()).acquire();
        }
        Preconditions.checkState(!res.isClosed(), String.format(
                "Pool %s return closed connection, even pool restart didn't help.. "
                        + "something is terribly wrong", getType()));
        return res;
    }

    @Override
    public DbType getType() {
        return DbType.DOCUMENT;
    }
}
