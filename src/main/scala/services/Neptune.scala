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
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__._
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality
import org.slf4j.LoggerFactory
import scala.language.implicitConversions
import NeptuneProperty.Implicits._

import scala.jdk.CollectionConverters._
import scala.util.Try

class Neptune(cluster: Cluster) {
  private val logger = LoggerFactory.getLogger(getClass)
  def withTraversal(func: GraphTraversalSource=>Unit) = {
    val g = traversal().withRemote(DriverRemoteConnection.using(cluster))
    try {
      func(g)
    } finally {
      g.close()
    }
  }

  private def upsertRecipeNode(recipe:Recipe)(implicit g:GraphTraversalSource) = {

    val nodeProps:Map[String, Option[NeptuneProperty]] = Map(
      "title"->Some(recipe.title),
      "description"->Some(recipe.description),
      "bookCredit"->recipe.bookCredit,
      "composerId"->recipe.composerId,
      "serves"->Some(recipe.serves.asJson.noSpaces),
      "featuredImage"->Some(recipe.featuredImage.asJson.noSpaces),
      "timings"->Some(recipe.timings.map(_.asJson.noSpaces)),
      "instructions"->Some(recipe.instructions.map(_.asJson.noSpaces))
    )
    g.V().has(T.id, recipe.id)
      .fold()
      .coalesce(addProps[Vertex,Vertex](__.unfold(), nodeProps), addProps[Vertex,Vertex](__.addV("Recipe").property(T.id, recipe.id), nodeProps))
      .next()
  }

  /**
   * Adds the given map of properties to the given Traversal. If a property value is None it will be skipped.
   * @param from
   * @param props
   * @tparam T
   * @return
   */
  private def addProps[A,B](from:GraphTraversal[A, B], props:Map[String, Option[NeptuneProperty]]):GraphTraversal[A, B] = {
    props.foldLeft(from)((node, prop)=>{
      prop._2 match {
        case Some(StringProperty(value))=>
          node.property(Cardinality.single, prop._1, value)
        case Some(IntProperty(value))=>
          node.property(Cardinality.single, prop._1, value)
        case Some(DoubleProperty(value))=>
          node.property(Cardinality.single, prop._1, value)
        case Some(BoolProperty(value))=>
          node.property(Cardinality.single, prop._1, value)
        case Some(p @SetProperty(_))=>
//          val explodedValues = value
//            .toList
//            .flatMap((kv)=>Seq(kv._1, kv._2))
          node.property(Cardinality.set, prop._1, p.getUntyped : _*)
        case Some(p@ListProperty(_))=>
          node.property(Cardinality.list, prop._1, p.getUntyped :_*)
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
    val edgeProps:Map[String, Option[NeptuneProperty]] = Map(
      "RecipeSection"->recipeSection,
      "AmountMin"->ingredient.amount.flatMap(_.min),
      "AmountMax"->ingredient.amount.flatMap(_.max),
      "Unit"->ingredient.unit,
      "Text"->ingredient.text,
      "Prefix"->ingredient.prefix,
      "Suffix"->ingredient.suffix,
      "Optional"->ingredient.optional,
    )

    val ingredNode = g.V().has("Name", ingredient.name)
      .fold()
      .coalesce(__.unfold(), __.addV("Ingredient").property("Name", ingredient.name))
      .next()
    upsertEdge(ingredNode, parent, "IngredientOf", edgeProps)
  }

  private def upsertEdge(source:Vertex, parent:Vertex, name:String, edgeProps:Map[String, Option[NeptuneProperty]] = Map())(implicit g:GraphTraversalSource) =
    g.V(source).outE(name).filter(inV().is(parent))
      .fold()
      .coalesce(__.unfold(), addProps[Vertex, Edge](__.V(source).addE(name).to(__.V(parent)), edgeProps))
      .next()

  private def upsertStringNodeNode(nodeType: String, edgeType:String, str: String, recipe: Vertex)(implicit g:GraphTraversalSource) = {
    if(nodeType=="" || edgeType=="" || str=="") {
      None
    } else {

      val contribVertex = g.V().has(T.id, str)
        .fold()
        .coalesce(__.unfold(), __.addV(nodeType).property(T.id, str))
        .next()

      upsertEdge(contribVertex, recipe, edgeType)
      Some(contribVertex)
    }
  }

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
      } yield ingredient

      recipe.contributors.foreach(contrib=>upsertStringNodeNode("Contributor", "ContributedTo", contrib, recepNode))
      recipe.cuisineIds.foreach(id=>upsertStringNodeNode("CuisineId", "CuisineOf", id, recepNode))
      recipe.suitableForDietIds.foreach(id=>upsertStringNodeNode("DietId", "SuitableDiet", id, recepNode))
      recipe.techniquesUsedIds.foreach(id=>upsertStringNodeNode("Technique", "UsedIn", id, recepNode))
      recipe.mealTypeIds.foreach(id=>upsertStringNodeNode("MealType", "MealType", id, recepNode))
      upsertStringNodeNode("CanonicalArticle","CanonicalArticle", recipe.canonicalArticle, recepNode)
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
