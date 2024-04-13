package models

case class RecipeImage(
                        url: String,
                        mediaId: String,
                        cropId: String,
                        source: Option[String] = None,
                        photographer: Option[String] = None,
                        imageType: Option[String] = None,
                        caption: Option[String] = None,
                        mediaApiUri: Option[String] = None,
                        width: Option[Int],
                        height: Option[Int]
                      )

case class Amount(min: Option[Float], max: Option[Float])

case class IngredientData(
                           name: String,
                           amount: Option[Amount],
                           unit: Option[String],
                           prefix: Option[String],
                           suffix: Option[String],
                           text: Option[String],
                           optional: Option[Boolean]
                         )

case class Ingredient(recipeSection: String, ingredientsList: Seq[IngredientData])

case class Serves(amount: Amount, unit: Option[String], text: Option[String])

case class Timing(qualifier: String, durationInMins: Option[Amount], text: Option[String])

case class Instruction(description: String, images: Option[Seq[RecipeImage]])


case class Recipe(
                             id: String,
                             composerId: Option[String],
                             canonicalArticle: String,
                             title: String,
                             description: String,
                             isAppReady: Option[Boolean],
                             featuredImage: Option[RecipeImage],
                             contributors: Seq[String],
                             ingredients: Seq[Ingredient],
                             suitableForDietIds: Seq[String],
                             cuisineIds: Seq[String],
                             mealTypeIds: Seq[String],
                             celebrationIds: Seq[String],
                             utensilsAndApplianceIds: Seq[String],
                             techniquesUsedIds: Seq[String],
                             difficultyLevel: Option[String],
                             serves: Seq[Serves],
                             timings: Seq[Timing],
                             instructions: Seq[Instruction],
                             bookCredit: Option[String]
                           )
