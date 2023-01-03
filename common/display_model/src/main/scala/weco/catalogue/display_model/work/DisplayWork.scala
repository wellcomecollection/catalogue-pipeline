package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.identifiers.DisplayIdentifier
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.locations.DisplayLocation
import weco.catalogue.internal_model.work.{Work, WorkState, WorkType}

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
  currentFrequency: Option[String],
  formerFrequency: List[String],
  designation: List[String],
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
      id = work.state.canonicalId.underlying,
      title = work.data.title,
      alternativeTitles = work.data.alternativeTitles,
      referenceNumber = work.data.referenceNumber.map { _.underlying },
      description = work.data.description,
      physicalDescription = work.data.physicalDescription,
      workType = work.data.format.map { DisplayFormat(_) },
      lettering = work.data.lettering,
      createdDate = work.data.createdDate.map { DisplayPeriod(_) },
      contributors = work.data.contributors.map {
        DisplayContributor(_, includesIdentifiers = true)
      },
      identifiers = work.identifiers.map { DisplayIdentifier(_) },
      subjects = work.data.subjects.map {
        DisplaySubject(_, includesIdentifiers = true)
      },
      genres = work.data.genres.map {
        DisplayGenre(_, includesIdentifiers = true)
      },
      thumbnail = work.data.thumbnail.map { DisplayLocation(_) },
      items = work.data.items.map { DisplayItem(_) },
      holdings = work.data.holdings.map { DisplayHoldings(_) },
      availabilities = work.state.availabilities.toList.map {
        DisplayAvailability(_)
      },
      production = work.data.production.map { DisplayProductionEvent(_) },
      languages = work.data.languages.map { DisplayLanguage(_) },
      edition = work.data.edition,
      notes = DisplayNote.merge(work.data.notes.map(DisplayNote(_))),
      duration = work.data.duration,
      currentFrequency = work.data.currentFrequency,
      formerFrequency = work.data.formerFrequency,
      designation = work.data.designation,
      images = work.data.imageData.map(DisplayWorkImageInclude(_)),
      partOf = DisplayPartOf(work.state.relations.ancestors),
      parts = work.state.relations.children.map(DisplayRelation(_)),
      precededBy =
        work.state.relations.siblingsPreceding.map(DisplayRelation(_)),
      succeededBy =
        work.state.relations.siblingsSucceeding.map(DisplayRelation(_)),
      ontologyType = displayWorkType(work.data.workType)
    )

  def displayWorkType(workType: WorkType): String = workType match {
    case WorkType.Standard   => "Work"
    case WorkType.Collection => "Collection"
    case WorkType.Series     => "Series"
    case WorkType.Section    => "Section"
  }
}
