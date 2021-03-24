package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}

@Schema(
  name = "ImageSource",
  description = "A description of the entity from which an image was sourced"
)
case class DisplayImageSource(
  @Schema(
    description = "Identifer of the image source"
  ) id: String,
  @Schema(
    description = "The title of the image source"
  ) title: Option[String],
  @Schema(
    description = "The contributors associated with the image source"
  ) contributors: Option[List[DisplayContributor]],
  @Schema(
    description = "The languages of the image source"
  ) languages: Option[List[DisplayLanguage]],
  @Schema(
    description = "The genres that describe the content of the image source"
  ) genres: Option[List[DisplayGenre]],
  @JsonKey("type") @Schema(
    name = "type",
    description = "What kind of source this is"
  ) ontologyType: String
)

object DisplayImageSource {

  def apply(imageSource: ImageSource,
            includes: ImageIncludes): DisplayImageSource =
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
              .map(DisplayContributor(_, includesIdentifiers = false)))
        else None,
      languages =
        if (includes.`source.languages`)
          Some(parent.canonicalWork.data.languages.map(DisplayLanguage(_)))
        else None,
      genres =
        if (includes.`source.genres`)
          Some(
            parent.canonicalWork.data.genres
              .map(DisplayGenre(_, includesIdentifiers = false)))
        else None,
      ontologyType = "Work"
    )
}
