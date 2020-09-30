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
  case object Images extends WorkInclude
  case object Parts extends WorkInclude
  case object PartOf extends WorkInclude
  case object PrecededBy extends WorkInclude
  case object SucceededBy extends WorkInclude
}

case class BetterWorksIncludes(
  identifiers: Boolean,
  items: Boolean,
  subjects: Boolean,
  genres: Boolean,
  contributors: Boolean,
  production: Boolean,
  notes: Boolean,
  images: Boolean,
  parts: Boolean,
  partOf: Boolean,
  precededBy: Boolean,
  succeededBy: Boolean
) {
  def anyRelation: Boolean = parts || partOf || precededBy || succeededBy
}

case object BetterWorksIncludes {
  def apply(includes: WorkInclude*): BetterWorksIncludes =
    BetterWorksIncludes(
      identifiers = includes.contains(WorkInclude.Identifiers),
      items = includes.contains(WorkInclude.Items),
      subjects = includes.contains(WorkInclude.Subjects),
      genres = includes.contains(WorkInclude.Genres),
      contributors = includes.contains(WorkInclude.Contributors),
      production = includes.contains(WorkInclude.Production),
      notes = includes.contains(WorkInclude.Notes),
      images = includes.contains(WorkInclude.Images),
      parts = includes.contains(WorkInclude.Parts),
      partOf = includes.contains(WorkInclude.PartOf),
      precededBy = includes.contains(WorkInclude.PrecededBy),
      succeededBy = includes.contains(WorkInclude.SucceededBy),
  )

  def includeAll(): BetterWorksIncludes =
    BetterWorksIncludes(
      identifiers = true,
      items = true,
      subjects = true,
      genres = true,
      contributors = true,
      production = true,
      notes = true,
      images = true,
      parts = true,
      partOf = true,
      precededBy = true,
      succeededBy = true
    )
}

case class WorksIncludes(includes: List[WorkInclude]) {
  def identifiers: Boolean = includes.contains(WorkInclude.Identifiers)
  def items: Boolean = includes.contains(WorkInclude.Items)
  def subjects: Boolean = includes.contains(WorkInclude.Subjects)
  def genres: Boolean = includes.contains(WorkInclude.Genres)
  def contributors: Boolean = includes.contains(WorkInclude.Contributors)
  def production: Boolean = includes.contains(WorkInclude.Production)
  def notes: Boolean = includes.contains(WorkInclude.Notes)
  def images: Boolean = includes.contains(WorkInclude.Images)
  def parts: Boolean = includes.contains(WorkInclude.Parts)
  def partOf: Boolean = includes.contains(WorkInclude.PartOf)
  def precededBy: Boolean = includes.contains(WorkInclude.PrecededBy)
  def succeededBy: Boolean = includes.contains(WorkInclude.SucceededBy)
  def anyRelation: Boolean = parts || partOf || precededBy || succeededBy
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
    images: Boolean = false,
    parts: Boolean = false,
    partOf: Boolean = false,
    precededBy: Boolean = false,
    succeededBy: Boolean = false,
  ): WorksIncludes = WorksIncludes(
    List(
      if (identifiers) Some(Identifiers) else None,
      if (items) Some(Items) else None,
      if (subjects) Some(Subjects) else None,
      if (genres) Some(Genres) else None,
      if (contributors) Some(Contributors) else None,
      if (production) Some(Production) else None,
      if (notes) Some(Notes) else None,
      if (images) Some(Images) else None,
      if (parts) Some(Parts) else None,
      if (partOf) Some(PartOf) else None,
      if (precededBy) Some(PrecededBy) else None,
      if (succeededBy) Some(SucceededBy) else None,
    ).flatten
  )

  def includeAll(): WorksIncludes = WorksIncludes(
    identifiers = true,
    items = true,
    subjects = true,
    genres = true,
    contributors = true,
    production = true,
    notes = true,
    images = true,
    parts = true,
    partOf = true,
    precededBy = true,
    succeededBy = true
  )
}
