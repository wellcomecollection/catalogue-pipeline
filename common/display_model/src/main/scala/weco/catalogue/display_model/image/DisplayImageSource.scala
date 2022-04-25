package weco.catalogue.display_model.image

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.work.{DisplayContributor, DisplayGenre}
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}

case class DisplayImageSource(
  id: String,
  title: Option[String],
  contributors: List[DisplayContributor],
  languages: List[DisplayLanguage],
  genres: List[DisplayGenre],
  @JsonKey("type") ontologyType: String
)

object DisplayImageSource {

  def apply(imageSource: ImageSource): DisplayImageSource =
    imageSource match {
      case works: ParentWorks => DisplayImageSource(works)
    }

  private def apply(parent: ParentWorks): DisplayImageSource =
    new DisplayImageSource(
      id = parent.id.canonicalId.underlying,
      title = parent.canonicalWork.data.title,
      contributors = parent.canonicalWork.data.contributors
        .map { DisplayContributor(_, includesIdentifiers = false) },
      languages = parent.canonicalWork.data.languages.map { DisplayLanguage(_) },
      genres = parent.canonicalWork.data.genres
        .map { DisplayGenre(_, includesIdentifiers = false) },
      ontologyType = "Work"
    )
}
