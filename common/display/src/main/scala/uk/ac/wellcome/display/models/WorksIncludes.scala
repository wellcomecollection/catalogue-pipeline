package uk.ac.wellcome.display.models

trait WorksIncludes

case class V2WorksIncludes(
  identifiers: Boolean = false,
  items: Boolean = false,
  subjects: Boolean = false,
  genres: Boolean = false,
  contributors: Boolean = false,
  production: Boolean = false,
  notes: Boolean = false,
  dissertation: Boolean = false,
) extends WorksIncludes

object V2WorksIncludes {

  val recognisedIncludes = List(
    "identifiers",
    "items",
    "subjects",
    "genres",
    "contributors",
    "production",
    "notes",
    "dissertation",
  )

  def apply(includesList: List[String]): V2WorksIncludes = V2WorksIncludes(
    identifiers = includesList.contains("identifiers"),
    items = includesList.contains("items"),
    subjects = includesList.contains("subjects"),
    genres = includesList.contains("genres"),
    contributors = includesList.contains("contributors"),
    production = includesList.contains("production"),
    notes = includesList.contains("notes"),
    dissertation = includesList.contains("dissertation"),
  )

  def includeAll() = V2WorksIncludes(recognisedIncludes)
}
