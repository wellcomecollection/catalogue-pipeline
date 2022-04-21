package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.{Work, WorkState, WorkType}

case class DisplayWork(
  id: String,
  title: Option[String],
  alternativeTitles: List[String],
  referenceNumber: Option[String] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  workType: Option[DisplayFormat] = None,
  lettering: Option[String] = None,
  createdDate: Option[DisplayPeriod] = None,
  contributors: Option[List[DisplayContributor]] = None,
  identifiers: Option[List[DisplayIdentifier]] = None,
  subjects: Option[List[DisplaySubject]] = None,
  genres: Option[List[DisplayGenre]] = None,
  thumbnail: Option[DisplayLocation] = None,
  items: Option[List[DisplayItem]] = None,
  holdings: Option[List[DisplayHoldings]] = None,
  availabilities: List[DisplayAvailability] = Nil,
  production: Option[List[DisplayProductionEvent]] = None,
  languages: Option[List[DisplayLanguage]] = None,
  edition: Option[String] = None,
  notes: Option[List[DisplayNote]] = None,
  duration: Option[Int] = None,
  images: Option[List[DisplayWorkImageInclude]] = None,
  parts: Option[List[DisplayRelation]] = None,
  partOf: Option[List[DisplayRelation]] = None,
  precededBy: Option[List[DisplayRelation]] = None,
  succeededBy: Option[List[DisplayRelation]] = None,
  @JsonKey("type") ontologyType: String = "Work"
)

object DisplayWork {

  def apply(
    work: Work.Visible[WorkState.Indexed],
    includes: WorksIncludes
  ): DisplayWork =
    DisplayWork(
      id = work.state.canonicalId.underlying,
      title = work.data.title,
      alternativeTitles = work.data.alternativeTitles,
      referenceNumber = work.data.referenceNumber.map { _.underlying },
      description = work.data.description,
      physicalDescription = work.data.physicalDescription,
      lettering = work.data.lettering,
      createdDate = work.data.createdDate.map { DisplayPeriod(_) },
      contributors = if (includes.contributors) {
        Some(work.data.contributors.map {
          DisplayContributor(_, includesIdentifiers = includes.identifiers)
        })
      } else None,
      subjects = if (includes.subjects) {
        Some(work.data.subjects.map {
          DisplaySubject(_, includesIdentifiers = includes.identifiers)
        })
      } else None,
      genres = if (includes.genres) {
        Some(work.data.genres.map {
          DisplayGenre(_, includesIdentifiers = includes.identifiers)
        })
      } else None,
      identifiers =
        if (includes.identifiers)
          Some(work.identifiers.map { DisplayIdentifier(_) })
        else None,
      workType = work.data.format.map { DisplayFormat(_) },
      thumbnail = work.data.thumbnail.map { DisplayLocation(_) },
      items =
        if (includes.items)
          Some(work.data.items.map {
            DisplayItem(_, includesIdentifiers = includes.identifiers)
          })
        else None,
      holdings =
        if (includes.holdings)
          Some(work.data.holdings.map { DisplayHoldings(_) })
        else None,
      availabilities = work.state.availabilities.toList.map {
        DisplayAvailability(_)
      },
      production =
        if (includes.production) Some(work.data.production.map {
          DisplayProductionEvent(_, includesIdentifiers = includes.identifiers)
        })
        else None,
      languages =
        if (includes.languages)
          Some(work.data.languages.map { DisplayLanguage(_) })
        else None,
      edition = work.data.edition,
      notes =
        if (includes.notes)
          Some(DisplayNote.merge(work.data.notes.map(DisplayNote(_))))
        else None,
      duration = work.data.duration,
      images =
        if (includes.images)
          Some(work.data.imageData.map(DisplayWorkImageInclude(_)))
        else None,
      ontologyType = displayWorkType(work.data.workType),
      partOf = if (includes.partOf) {
        Some(DisplayPartOf(work.state.relations.ancestors))
      } else None,
      parts =
        if (includes.parts)
          Some(
            work.state.relations.children.map(DisplayRelation(_))
          )
        else None,
      precededBy =
        if (includes.precededBy)
          Some(
            work.state.relations.siblingsPreceding.map(DisplayRelation(_))
          )
        else None,
      succeededBy =
        if (includes.succeededBy)
          Some(
            work.state.relations.siblingsSucceeding.map(DisplayRelation(_))
          )
        else None
    )

  def apply(work: Work.Visible[WorkState.Indexed]): DisplayWork =
    DisplayWork(work = work, includes = WorksIncludes.none)

  def displayWorkType(workType: WorkType): String = workType match {
    case WorkType.Standard   => "Work"
    case WorkType.Collection => "Collection"
    case WorkType.Series     => "Series"
    case WorkType.Section    => "Section"
  }
}
