import org.slf4j.LoggerFactory
import services.{Neptune, RecipesApi}

import scala.util.{Failure, Success}

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]):Unit = {
    val repi = new RecipesApi("https://recipes.guardianapis.com")
    Neptune("localhost", 8182, unsafe = true) match {
      case Success(nep) =>
        repi.getIndex() match {
          case Left(err) =>
            logger.error(err)
          case Right(content) =>
            logger.info(s"Got ${content.recipes.length} recipes")
            nep.withTraversal { implicit graphTransact =>
              content.recipes.foreach(recipe => {
                repi.getRecipeContent(recipe) match {
                  case Left(err) =>
                    logger.error("ERROR Couldn't get one recipe: ", err)
                  case Right(content) =>
                    nep.addRecipe(content) match {
                      case Success(_) =>
                        logger.info(s"Added recipe for '${content.title}'")
                      case Failure(err) =>
                        logger.error(s"Could not insert '${content.title}': ${err.getMessage}", err)
                    }
                }
              })
            }
        }
        nep.shutdown
      case Failure(err) =>
        println(s"ERROR Could not connect to Neptune: ${err.getMessage}")
    }
  }
}
