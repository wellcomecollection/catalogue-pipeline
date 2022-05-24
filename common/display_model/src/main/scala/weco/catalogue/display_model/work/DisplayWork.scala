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

  def apply(work: Work.Visible[WorkState.Indexed]): DisplayWork =
    DisplayWork(
      id = work.state.canonicalId,
      workData = workData
    )
  
  def apply(
    id: CanonicalId,
    workData: WorkData[DataState.Identified]): DisplayWork =
    DisplayWork(
      id = id.underlying,
      title = workData.title,
      alternativeTitles = workData.alternativeTitles,
      referenceNumber = workData.referenceNumber.map { _.underlying },
      description = workData.description,
      physicalDescription = workData.physicalDescription,
      workType = workData.format.map { DisplayFormat(_) },
      lettering = workData.lettering,
      createdDate = workData.createdDate.map { DisplayPeriod(_) },
      contributors = workData.contributors.map {
        DisplayContributor(_, includesIdentifiers = true)
      },
      identifiers = work.identifiers.map { DisplayIdentifier(_) },
      subjects = workData.subjects.map {
        DisplaySubject(_, includesIdentifiers = true)
      },
      genres = workData.genres.map {
        DisplayGenre(_, includesIdentifiers = true)
      },
      thumbnail = workData.thumbnail.map { DisplayLocation(_) },
      items = workData.items.map { DisplayItem(_) },
      holdings = workData.holdings.map { DisplayHoldings(_) },
      availabilities = work.state.availabilities.toList.map {
        DisplayAvailability(_)
      },
      production = workData.production.map { DisplayProductionEvent(_) },
      languages = workData.languages.map { DisplayLanguage(_) },
      edition = workData.edition,
      notes = DisplayNote.merge(workData.notes.map(DisplayNote(_))),
      duration = workData.duration,
      images = workData.imageData.map(DisplayWorkImageInclude(_)),
      partOf = DisplayPartOf(work.state.relations.ancestors),
      parts = work.state.relations.children.map(DisplayRelation(_)),
      precededBy =
        work.state.relations.siblingsPreceding.map(DisplayRelation(_)),
      succeededBy =
        work.state.relations.siblingsSucceeding.map(DisplayRelation(_)),
      ontologyType = displayWorkType(workData.workType)
    )

  def displayWorkType(workType: WorkType): String = workType match {
    case WorkType.Standard   => "Work"
    case WorkType.Collection => "Collection"
    case WorkType.Series     => "Series"
    case WorkType.Section    => "Section"
  }
}
