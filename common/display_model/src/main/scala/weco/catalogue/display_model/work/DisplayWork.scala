package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.identifiers.DisplayIdentifier
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.locations.DisplayLocation
import weco.catalogue.internal_model.identifiers.{CanonicalId, DataState}
import weco.catalogue.internal_model.work.{Work, WorkData, WorkState, WorkType}

case class DisplayWork(
  id: String,
  title: Option[String],
  alternativeTitles: List[String],
  referenceNumber: Option[String],
  description: Option[String],
  physicalDescription: Option[String],
  workType: Option[DisplayFormat],
  lettering: Option[String],
  createdDate: Option[DisplayPeriod],
  contributors: List[DisplayContributor],
  identifiers: List[DisplayIdentifier],
  subjects: List[DisplaySubject],
  genres: List[DisplayGenre],
  thumbnail: Option[DisplayLocation] = None,
  items: List[DisplayItem],
  holdings: List[DisplayHoldings],
  availabilities: List[DisplayAvailability],
  production: List[DisplayProductionEvent],
  languages: List[DisplayLanguage],
  edition: Option[String] = None,
  notes: List[DisplayNote],
  duration: Option[Int],
  images: List[DisplayWorkImageInclude],
  parts: List[DisplayRelation],
  partOf: List[DisplayRelation],
  precededBy: List[DisplayRelation],
  succeededBy: List[DisplayRelation],
  @JsonKey("type") ontologyType: String = "Work"
)

object DisplayWork {

  def apply(work: Work.Visible[WorkState.Denormalised]): DisplayWork =
    DisplayWork(
      id = work.state.canonicalId,
      data = work.data
    )
  
  def apply(
    id: CanonicalId,
    data: WorkData[DataState.Identified]): DisplayWork =
    DisplayWork(
      id = id.underlying,
      title = data.title,
      alternativeTitles = data.alternativeTitles,
      referenceNumber = data.referenceNumber.map { _.underlying },
      description = data.description,
      physicalDescription = data.physicalDescription,
      workType = data.format.map { DisplayFormat(_) },
      lettering = data.lettering,
      createdDate = data.createdDate.map { DisplayPeriod(_) },
      contributors = data.contributors.map {
        DisplayContributor(_, includesIdentifiers = true)
      },
      identifiers = work.identifiers.map { DisplayIdentifier(_) },
      subjects = data.subjects.map {
        DisplaySubject(_, includesIdentifiers = true)
      },
      genres = data.genres.map {
        DisplayGenre(_, includesIdentifiers = true)
      },
      thumbnail = data.thumbnail.map { DisplayLocation(_) },
      items = data.items.map { DisplayItem(_) },
      holdings = data.holdings.map { DisplayHoldings(_) },
      availabilities = work.state.availabilities.toList.map {
        DisplayAvailability(_)
      },
      production = data.production.map { DisplayProductionEvent(_) },
      languages = data.languages.map { DisplayLanguage(_) },
      edition = data.edition,
      notes = DisplayNote.merge(data.notes.map(DisplayNote(_))),
      duration = data.duration,
      images = data.imageData.map(DisplayWorkImageInclude(_)),
      partOf = DisplayPartOf(work.state.relations.ancestors),
      parts = work.state.relations.children.map(DisplayRelation(_)),
      precededBy =
        work.state.relations.siblingsPreceding.map(DisplayRelation(_)),
      succeededBy =
        work.state.relations.siblingsSucceeding.map(DisplayRelation(_)),
      ontologyType = displayWorkType(data.workType)
    )

  def displayWorkType(workType: WorkType): String = workType match {
    case WorkType.Standard   => "Work"
    case WorkType.Collection => "Collection"
    case WorkType.Series     => "Series"
    case WorkType.Section    => "Section"
  }
}
