package ru.vyarus.guice.persist.orient.repository.mixin.graph

import ru.vyarus.guice.persist.orient.AbstractTest
import ru.vyarus.guice.persist.orient.repository.mixin.graph.support.EdgeTypeDao
import ru.vyarus.guice.persist.orient.support.model.EdgeModel
import ru.vyarus.guice.persist.orient.support.model.VertexModel
import ru.vyarus.guice.persist.orient.support.modules.RepositoryTestModule
import ru.vyarus.guice.persist.orient.util.transactional.TransactionalTest
import spock.guice.UseModules

import javax.inject.Inject

/**
 * @author Vyacheslav Rusakov 
 * @since 26.06.2015
 */
@UseModules(RepositoryTestModule)
class EdgeTypeSupportTest extends AbstractTest {

    @Inject
    EdgeTypeDao dao

    @TransactionalTest
    def "Check edge type mixin"() {

        setup:
        VertexModel test = dao.save(new VertexModel(name: 'test1'))
        VertexModel test2 = dao.save(new VertexModel(name: 'test2'))

        when: "creating edge"
        EdgeModel edge = dao.createEdge(test, test2);
        then: "edge created"
        edge != null

        when: "updating edge property"
        edge.name = 'edge test'
        dao.updateEdge(edge)
        then: "ok"
        true

        when: "searching for edge"
        edge = dao.findEdge(test, test2)
        then: "found"
        edge != null
        edge.name == 'edge test'

        then: "find counts direction"
        dao.findEdge(test2, test) == null

        then: "find between correct"
        dao.findEdgeBetween(test2, test) != null

        when: 'removing edge'
        dao.deleteEdge(edge)
        then: 'removed'
        dao.findEdge(test, test2) == null

        when: 'remove edge between nodes'
        dao.createEdge(test, test2)
        int deleted = dao.deleteEdge(test, test2)
        then: 'removed'
        deleted == 1
        dao.findEdge(test, test2) == null

        when: 'creating edge with object'
        edge = dao.createEdge(test, test2, new EdgeModel(name: 'edge test'))
        then: 'ok'
        edge.name == 'edge test'
    }

    def "Check pure object id restore after transaction"() {

        setup:
        VertexModel test = dao.save(new VertexModel(name: 'test1'))
        VertexModel test2 = dao.save(new VertexModel(name: 'test2'))

        when: "saving raw entity"
        EdgeModel model = dao.createEdge(test, test2)
        then: "id correct"
        dao.getEdge(model.getId()) != null

    }

    def "Check double delete"() {

        setup:
        VertexModel test = dao.save(new VertexModel(name: 'test1'))
        VertexModel test2 = dao.save(new VertexModel(name: 'test2'))

        when: "creating and removing edge pojo"
        String id = dao.createEdge(test, test2).id
        dao.deleteEdge(id)
        dao.deleteEdge(id)
        then: "second delete successful"

    }
}