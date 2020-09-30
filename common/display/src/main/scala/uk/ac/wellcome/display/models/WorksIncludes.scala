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

case class WorksIncludes(
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

case object WorksIncludes {
  def apply(includes: WorkInclude*): WorksIncludes =
    WorksIncludes(
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

  def includeAll(): WorksIncludes =
    WorksIncludes(
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
