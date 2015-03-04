package ru.vyarus.guice.persist.orient.support;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.google.inject.persist.PersistService;

/**
 * Base class for provided scheme initializers.
 * If no package specified then assuming root package should be used.
 *
 * @author Vyacheslav Rusakov
 * @since 02.03.2015
 */
public abstract class AbstractSchemeModule extends AbstractModule {

    private final String pkg;

    public AbstractSchemeModule(final String pkg) {
        this.pkg = pkg;
    }

    @Override
    protected void configure() {
        // prevent usage without main OrientModule
        requireBinding(PersistService.class);

        // if package not provided empty string will mean root package (search all classpath)
        // not required if provided scheme initializers not used
        bindConstant().annotatedWith(Names.named("orient.model.package")).to(Strings.nullToEmpty(pkg));

        bindSchemeInitializer();
    }

    /**
     * Bind scheme initializer implementation.
     *
     * @see ru.vyarus.guice.persist.orient.db.scheme.SchemeInitializer
     */
    protected abstract void bindSchemeInitializer();
}
