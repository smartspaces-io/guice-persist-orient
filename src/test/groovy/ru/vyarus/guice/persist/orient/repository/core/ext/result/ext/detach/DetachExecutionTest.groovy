package ru.vyarus.guice.persist.orient.repository.core.ext.result.ext.detach

import com.google.inject.Inject
import ru.vyarus.guice.persist.orient.AbstractTest
import ru.vyarus.guice.persist.orient.repository.core.MethodExecutionException
import ru.vyarus.guice.persist.orient.support.model.Model
import ru.vyarus.guice.persist.orient.support.modules.BootstrapModule
import ru.vyarus.guice.persist.orient.support.modules.RepositoryTestModule
import spock.guice.UseModules

/**
 * @author Vyacheslav Rusakov 
 * @since 02.03.2015
 */
@UseModules([RepositoryTestModule, BootstrapModule])
class DetachExecutionTest extends AbstractTest {

    @Inject
    DetachCases repository

    def "Check detach"() {

        when: "check objects cant be used outside of transaction"
        List<Model> res = repository.select();
        res[0].name
        then: "error: proxy cant be used outside of transaction"
        thrown(NullPointerException)

        when: "detach list of objects"
        res = repository.selectDetach()
        then: "detached"
        res.size() == 10
        res[0].name == 'name0'

        when: "detach plain object"
        def res2 = repository.selectPlainDetach()
        then: "detached"
        res2.name == 'name0'

        when: "detach array object"
        res2 = repository.selectArrayDetach()
        then: "detached"
        res2.length == 10
        res2[0].name == 'name0'

        when: "detach set object"
        res2 = repository.selectSetDetach()
        then: "detached"
        res2.size() == 10
        res2.iterator().next().name.startsWith('name')

        when: "detach custom collection object"
        res2 = repository.selectCustomCollectionDetach()
        then: "detached"
        res2 instanceof LinkedList
        res2.size() == 10
        res2.iterator().next().name.startsWith('name')

        when: "trying to detach pure string"
        res2 = repository.noActualDetach()
        then: "no error, because orient support this case and do nothing"
        res2 == 'name0'

        when: "using wrong connection"
        repository.detachError()
        then: "error, only object connection allowed"
        thrown(MethodExecutionException)
    }
}