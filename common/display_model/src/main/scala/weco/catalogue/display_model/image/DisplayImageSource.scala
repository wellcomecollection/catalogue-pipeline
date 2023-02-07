package weco.catalogue.display_model.image

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.work.{
  DisplayContributor,
  DisplayGenre,
  DisplaySubject
}
import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.image.{ImageSource, ParentWork}
import weco.catalogue.internal_model.work.WorkData

case class DisplayImageSource(
  id: String,
  title: Option[String],
  contributors: List[DisplayContributor],
  languages: List[DisplayLanguage],
  genres: List[DisplayGenre],
  subjects: List[DisplaySubject],
  @JsonKey("type") ontologyType: String
)

object DisplayImageSource {

  def apply(imageSource: ImageSource): DisplayImageSource =
    imageSource match {
      case ParentWork(id, workData, _) => DisplayImageSource(id, workData)
    }

  private def apply(
    id: IdState.Identified,
    data: WorkData[DataState.Identified]
  ): DisplayImageSource =
    new DisplayImageSource(
      id = id.canonicalId.underlying,
      title = data.title,
      contributors = data.contributors
        .map { DisplayContributor(_, includesIdentifiers = false) },
      languages = data.languages.map { DisplayLanguage(_) },
      genres = data.genres
        .map { DisplayGenre(_, includesIdentifiers = false) },
      subjects = data.subjects
        .map { DisplaySubject(_, includesIdentifiers = false) },
      ontologyType = "Work"
    )
}
