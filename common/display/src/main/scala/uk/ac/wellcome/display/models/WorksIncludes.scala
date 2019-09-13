package uk.ac.wellcome.display.models

case class WorksIncludes(
  identifiers: Boolean = false,
  items: Boolean = false,
  subjects: Boolean = false,
  genres: Boolean = false,
  contributors: Boolean = false,
  production: Boolean = false
)

object WorksIncludes {
  val recognisedIncludes = List(
    "identifiers",
    "items",
    "subjects",
    "genres",
    "contributors",
    "production")
  def apply(includesList: List[String]): WorksIncludes = WorksIncludes(
    identifiers = includesList.contains("identifiers"),
    items = includesList.contains("items"),
    subjects = includesList.contains("subjects"),
    genres = includesList.contains("genres"),
    contributors = includesList.contains("contributors"),
    production = includesList.contains("production")
  )

  def includeAll() = WorksIncludes(recognisedIncludes)
}
