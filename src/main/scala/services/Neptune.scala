package services

import groovy.util.ResourceException
import models.Recipe
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.Client
import org.apache.tinkerpop.gremlin.driver.message.ResponseStatusCode
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.structure.{T, Vertex}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import scala.jdk.CollectionConverters._
import scala.util.Try

class Neptune(cluster: Cluster) {
  def withTraversal(func: GraphTraversalSource=>Unit) = {
    val g = traversal().withRemote(DriverRemoteConnection.using(cluster))
    try {
      func(g)
    } finally {
      g.close()
    }
  }

  private def upsertRecipeNode(recipe:Recipe)(implicit g:GraphTraversalSource) = {
    g.V().has(T.id, recipe.id)
      .fold()
      .coalesce(__.unfold().property("Hello", 1), __.addV("Recipe").property(T.id, recipe.id).property("Title", recipe.title).property("Description", recipe.description))

      .iterate()
  }

  def addRecipe(recipe:Recipe)(implicit g:GraphTraversalSource) = {
    Try {
      val recepNode = upsertRecipeNode(recipe)
    }
  }
  def shutdown = {
    cluster.close()
  }
}

object Neptune {
  def apply(host:String, port:Int, unsafe:Boolean) = {
    val builder = Cluster.build()
    builder.addContactPoint(host)
    builder.port(port)
    builder.enableSsl(true)
    builder.sslSkipCertValidation(unsafe)

    Try {
      val cluster = builder.create();
      new Neptune(cluster)
    }
  }

  private def test() = {
        val builder = Cluster.build()
        builder.addContactPoint("localhost")
        builder.port(8182)
        builder.enableSsl(true)
        builder.sslSkipCertValidation(true)

        val cluster = builder.create();

        val g = traversal().withRemote(DriverRemoteConnection.using(cluster))

        // Add a vertex.
        // Note that a Gremlin terminal step, e.g. iterate(), is required to make a request to the remote server.
        // The full list of Gremlin terminal steps is at https://tinkerpop.apache.org/docs/current/reference/#terminal-steps
        g.addV("Person").property("Name", "Justin").iterate()

        // Add a vertex with a user-supplied ID.
        g.addV("Custom Label").property(T.id, "CustomId1").property("name", "Custom id vertex 1").iterate()
        g.addV("Custom Label").property(T.id, "CustomId2").property("name", "Custom id vertex 2").iterate()

        g.addE("Edge Label").from(__.V("CustomId1")).to(__.V("CustomId2")).iterate()

        // This gets the vertices, only.
        val t = g.V().limit(3).elementMap()

        t.forEachRemaining(
          e =>System.out.println(t.toList())
        );

        cluster.close();
  }
}
