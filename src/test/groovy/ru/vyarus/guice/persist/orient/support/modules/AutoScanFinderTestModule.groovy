package ru.vyarus.guice.persist.orient.support.modules

import com.google.inject.AbstractModule
import ru.vyarus.guice.persist.orient.support.AutoScanFinderModule

/**
 * @author Vyacheslav Rusakov 
 * @since 04.08.2014
 */
@Deprecated
class AutoScanFinderTestModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new PackageSchemeModule())
        install(new AutoScanFinderModule("ru.vyarus.guice.persist.orient.support"));
    }
}
