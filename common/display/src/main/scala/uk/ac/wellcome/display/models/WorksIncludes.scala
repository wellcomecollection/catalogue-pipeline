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
  case object Collection extends WorkInclude
  case object Images extends WorkInclude
  case object Parts extends WorkInclude
  case object PartOf extends WorkInclude
  case object PreceededBy extends WorkInclude
  case object SuceededBy extends WorkInclude
}

case class WorksIncludes(includes: List[WorkInclude]) {
  def identifiers = includes.contains(WorkInclude.Identifiers)
  def items = includes.contains(WorkInclude.Items)
  def subjects = includes.contains(WorkInclude.Subjects)
  def genres = includes.contains(WorkInclude.Genres)
  def contributors = includes.contains(WorkInclude.Contributors)
  def production = includes.contains(WorkInclude.Production)
  def notes = includes.contains(WorkInclude.Notes)
  def collection = includes.contains(WorkInclude.Collection)
  def images = includes.contains(WorkInclude.Images)
  def parts = includes.contains(WorkInclude.Parts)
  def partOf = includes.contains(WorkInclude.PartOf)
  def preceededBy = includes.contains(WorkInclude.PreceededBy)
  def suceededBy = includes.contains(WorkInclude.SuceededBy)
  def anyRelation = parts || partOf || preceededBy || suceededBy
}

object WorksIncludes {

  import WorkInclude._

  def apply(
    identifiers: Boolean = false,
    items: Boolean = false,
    subjects: Boolean = false,
    genres: Boolean = false,
    contributors: Boolean = false,
    production: Boolean = false,
    notes: Boolean = false,
    collection: Boolean = false,
    images: Boolean = false,
    parts: Boolean = false,
    partOf: Boolean = false,
    preceededBy: Boolean = false,
    suceededBy: Boolean = false,
  ): WorksIncludes = WorksIncludes(
    List(
      if (identifiers) Some(Identifiers) else None,
      if (items) Some(Items) else None,
      if (subjects) Some(Subjects) else None,
      if (genres) Some(Genres) else None,
      if (contributors) Some(Contributors) else None,
      if (production) Some(Production) else None,
      if (notes) Some(Notes) else None,
      if (collection) Some(Collection) else None,
      if (images) Some(Images) else None,
      if (parts) Some(Parts) else None,
      if (partOf) Some(PartOf) else None,
      if (preceededBy) Some(PreceededBy) else None,
      if (suceededBy) Some(SuceededBy) else None,
    ).flatten
  )

  def includeAll(): WorksIncludes = WorksIncludes(
    true, true, true, true, true, true, true, true, true, true, true, true, true
  )
}
