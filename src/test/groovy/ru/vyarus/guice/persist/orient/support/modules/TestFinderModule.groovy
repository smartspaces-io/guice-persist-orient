package ru.vyarus.guice.persist.orient.support.modules

import com.google.inject.AbstractModule
import ru.vyarus.guice.persist.orient.FinderModule

/**
 * @author Vyacheslav Rusakov 
 * @since 31.07.2014
 */
class TestFinderModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PackageSchemeModule())
        install(new FinderModule())
    }
}
