package services

import groovy.util.ResourceException
import models.{Ingredient, IngredientData, Recipe}
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.Client
import org.apache.tinkerpop.gremlin.driver.message.ResponseStatusCode
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.structure.{Edge, T, Vertex}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import io.circe.generic.auto._
import io.circe.syntax._

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
      .next()
  }

  /**
   * Adds the given map of properties to the given Traversal. If a property value is None it will be skipped.
   * @param from
   * @param props
   * @tparam T
   * @return
   */
  private def addProps[A,B](from:GraphTraversal[A, B], props:Map[String, Option[String]]):GraphTraversal[A, B] = {
    props.foldLeft(from)((node, prop)=>{
      prop._2 match {
        case Some(value)=>
          node.property(prop._1, value)
        case None=>
          node
      }
    })
  }

  /**
   * Returns a Vertex representing the given ingredient (recognised by name), inserting the vertex if necessary.
   * @param ingredient IngredientData object to insert
   * @param g implicitly provided GraphTraversalSource (like a connection, or a transaction)
   * @return GraphTraversal representing the given vertex. This can then be connected via Edges.
   */
  private def upsertIngredientNode(ingredient:IngredientData, recipeSection:Option[String], parent:Vertex)(implicit g:GraphTraversalSource) = {
    val edgeProps = Map(
      "RecipeSection"->recipeSection,
      "Amount"->Some(ingredient.amount.asJson.noSpaces),
      "Unit"->ingredient.unit,
      "Text"->ingredient.text,
      "Prefix"->ingredient.prefix,
      "Suffix"->ingredient.suffix,
      "Optional"->ingredient.optional.map(_.toString),
    )

    val ingredNode = g.V().has("Name", ingredient.name)
      .fold()
      .coalesce(__.unfold(), __.addV("Ingredient").property("Name", ingredient.name))
      .next()
      //.properties(edgeProps :_*)
//      .properties(
//        "RecipeSection", recipeSection.orNull,
//        "Amount", ingredient.amount.asJson.noSpaces,
//        "Unit", ingredient.unit.orNull,
//        "Text", ingredient.text.orNull,
//        "Prefix", ingredient.prefix.orNull,
//        "Suffix", ingredient.suffix.orNull,
//        "Optional", ingredient.optional.getOrElse(false).toString,
//      )
//    val edgeArgs = edgeProps.collect({ case (k, Some(v))=>k->v}).toSeq
//    ingredNode.addEdge("IngredientOf", parent, edgeArgs :_*)

    //addProps(g.V(ingredNode).addE("IngredientOf").to(parent), edgeProps).iterate()
//    val opWithProps = addProps(g.V("Ingredient").has("Name", ingredient.name).addE("IngredientOf").property(T.label, "IngredientOf"), edgeProps)
//
//      opWithProps.to(__.V("Recipe").has(T.id, recipeId)).iterate()
    upsertEdge(ingredNode, parent, "IngredientOf", edgeProps)
  }

  private def upsertEdge(source:Vertex, parent:Vertex, name:String, edgeProps:Map[String, Option[String]])(implicit g:GraphTraversalSource) =
    g.V(source).outE(name)
      .fold()
      .coalesce(__.unfold(), addProps[Vertex, Edge](__.V(source).addE(name).to(__.V(parent)), edgeProps))
      .next()
  /**
   * Adds the given recipe to the database
   * @param recipe Recipe object to insert
   * @param g implicitly provided GraphTraversalSource (like a connection, or a transaction)
   * @return a Try which gives success with no value or Failure on error.
   */
  def addRecipe(recipe:Recipe)(implicit g:GraphTraversalSource):Try[Unit] = {
    Try {
      //Upsert the vertex to represent the recipe
      val recepNode = upsertRecipeNode(recipe)
      //Upsert a vertex to represent each ingredient. Then, add an Edge to connect it to the recipe
      for {
        ingredSection <- recipe.ingredients
        ingredient <- ingredSection.ingredientsList
        _ = upsertIngredientNode(ingredient, ingredSection.recipeSection, recepNode)
//        _ = __.V("Ingredient").has("Name", ingredient.name).addE("Contains")
//          .to(__.V("Recipe").has(T.id, recipe.id))
//          .properties("RecipeSection", ingredSection.recipeSection.orNull,
//            "Amount", ingredient.amount.asJson.noSpaces,
//            "Unit", ingredient.unit.orNull,
//            "Text", ingredient.text.orNull,
//            "Prefix", ingredient.prefix.orNull,
//            "Suffix", ingredient.suffix.orNull,
//            "Optional", ingredient.optional.getOrElse(false).toString,
//          )
//          .iterate()
      } yield ingredient

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
