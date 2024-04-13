package services

import io.circe.Decoder
import io.circe.generic.auto._
import models.{Recipe, RecipeIndex, RecipeIndexEntry}
import sttp.client4.quick._
import sttp.model.StatusCode

class RecipesApi(baseUrl: String) {
  private def getBodyAs[T:Decoder](body:Either[String,String]) = for {
      bodyContentString <- body
      parsed <- io.circe.parser.parse(bodyContentString).left.map(_.toString)
      marshalled <- parsed.as[T].left.map(_.toString())
    } yield marshalled

  def getIndex():Either[String, RecipeIndex] = {
    val response = quickRequest
      .get(uri"$baseUrl/index.json")
      .response(asString("UTF-8"))
      .send()

    if(response.code==StatusCode.Ok) {
      getBodyAs[RecipeIndex](response.body)
    } else {
      Left(s"Server replied with ${response.code.code}")
    }
  }

  def getRecipeContent(checksum:String):Either[String, Recipe] = {
    val response = quickRequest
      .get(uri"$baseUrl/content/$checksum")
      .response(asString("UTF-8"))
      .send()

    if(response.code==StatusCode.Ok) {
      getBodyAs[Recipe](response.body)
    } else {
      Left(s"Server replied with ${response.code.code}")
    }
  }

  def getRecipeContent(entry:RecipeIndexEntry):Either[String, Recipe] = getRecipeContent(entry.checksum)
}
