package models

import java.time.ZonedDateTime

case class RecipeIndex(schemaVersion: Int, recipes: Seq[RecipeIndexEntry], lastUpdated:ZonedDateTime)
