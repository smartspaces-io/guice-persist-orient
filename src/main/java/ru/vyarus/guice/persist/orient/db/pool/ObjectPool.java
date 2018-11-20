package ru.vyarus.guice.persist.orient.db.pool;

import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.object.ODatabaseObject;
import com.orientechnologies.orient.object.db.OObjectDatabaseTx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vyarus.guice.persist.orient.db.DbType;
import ru.vyarus.guice.persist.orient.db.user.UserManager;

import javax.inject.Inject;

/**
 * Object pool implementation. Use document pool connection to merge object transaction with document transaction.
 *
 * @author Vyacheslav Rusakov
 * @since 24.07.2014
 */
public class ObjectPool implements PoolManager<ODatabaseObject> {
    private final Logger logger = LoggerFactory.getLogger(ObjectPool.class);

    private final ThreadLocal<ODatabaseObject> transaction = new ThreadLocal<ODatabaseObject>();
    private final DocumentPool documentPool;
    private final UserManager userManager;

    @Inject
    public ObjectPool(final DocumentPool documentPool, final UserManager userManager) {
        this.documentPool = documentPool;
        this.userManager = userManager;
    }

    @Override
    public void start(final String uri) {
        // test connection and let orient configure database
        new OObjectDatabaseTx(uri).open(userManager.getUser(), userManager.getPassword()).close();
        logger.debug("Pool {} started for '{}'", getType(), uri);
    }

    @Override
    public void stop() {
        // no stop logic
    }

    @Override
    public void commit() {
        transaction.remove();
    }

    @Override
    public void rollback() {
        transaction.remove();
    }

    @Override
    public ODatabaseObject get() {
        if (transaction.get() == null) {
            final ODatabaseDocumentInternal documentDb = (ODatabaseDocumentInternal) documentPool.get();
            final OObjectDatabaseTx value = new OObjectDatabaseTx(documentDb);
            transaction.set(value);
        }
        final ODatabaseObject db = transaction.get();
        db.activateOnCurrentThread();
        return db;
    }

    @Override
    public DbType getType() {
        return DbType.OBJECT;
    }
}
