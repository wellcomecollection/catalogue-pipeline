package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}

case class DisplayImageSource(
  id: String,
  title: Option[String],
  contributors: Option[List[DisplayContributor]],
  languages: Option[List[DisplayLanguage]],
  genres: Option[List[DisplayGenre]],
  @JsonKey("type") ontologyType: String
)

object DisplayImageSource {

  def apply(
    imageSource: ImageSource,
    includes: ImageIncludes
  ): DisplayImageSource =
    imageSource match {
      case works: ParentWorks =>
        DisplayImageSource(works, includes)
    }

  def apply(parent: ParentWorks, includes: ImageIncludes): DisplayImageSource =
    new DisplayImageSource(
      id = parent.id.canonicalId.underlying,
      title = parent.canonicalWork.data.title,
      contributors =
        if (includes.`source.contributors`)
          Some(
            parent.canonicalWork.data.contributors
              .map(DisplayContributor(_, includesIdentifiers = false))
          )
        else None,
      languages =
        if (includes.`source.languages`)
          Some(parent.canonicalWork.data.languages.map(DisplayLanguage(_)))
        else None,
      genres =
        if (includes.`source.genres`)
          Some(
            parent.canonicalWork.data.genres
              .map(DisplayGenre(_, includesIdentifiers = false))
          )
        else None,
      ontologyType = "Work"
    )
}
