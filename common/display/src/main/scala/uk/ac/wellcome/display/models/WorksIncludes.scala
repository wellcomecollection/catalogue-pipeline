package uk.ac.wellcome.display.models

sealed trait WorkInclude

object WorkInclude {
  case object Identifiers extends WorkInclude
  case object Items extends WorkInclude
  case object Subjects extends WorkInclude
  case object Genres extends WorkInclude
  case object Contributors extends WorkInclude
  case object Production extends WorkInclude
  case object Notes extends WorkInclude
}

trait WorksIncludes

case class V2WorksIncludes(includes: List[WorkInclude]) extends WorksIncludes {
  def identifiers = includes.contains(WorkInclude.Identifiers)
  def items = includes.contains(WorkInclude.Items)
  def subjects = includes.contains(WorkInclude.Subjects)
  def genres = includes.contains(WorkInclude.Genres)
  def contributors = includes.contains(WorkInclude.Contributors)
  def production = includes.contains(WorkInclude.Production)
  def notes = includes.contains(WorkInclude.Notes)
}

object V2WorksIncludes {

  import WorkInclude._

  def apply(
    identifiers: Boolean = false,
    items: Boolean = false,
    subjects: Boolean = false,
    genres: Boolean = false,
    contributors: Boolean = false,
    production: Boolean = false,
    notes: Boolean = false,
  ): V2WorksIncludes = V2WorksIncludes(
    List(
      if (identifiers) Some(Identifiers) else None,
      if (items) Some(Items) else None,
      if (subjects) Some(Subjects) else None,
      if (genres) Some(Genres) else None,
      if (contributors) Some(Contributors) else None,
      if (production) Some(Production) else None,
      if (notes) Some(Notes) else None,
    ).flatten
  )

  def includeAll(): V2WorksIncludes = V2WorksIncludes(
    true, true, true, true, true, true, true
  )
}
