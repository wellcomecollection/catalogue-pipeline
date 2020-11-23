package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

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
    description = "The primary contributor associated with the image source"
  ) contributor: Option[DisplayContributor],
  @Schema(
    description = "The languages of the image source"
  ) languages: Option[List[DisplayLanguage]],
  @JsonKey("type") @Schema(
    name = "type",
    description = "What kind of source this is"
  ) ontologyType: String
)

object DisplayImageSource {

  def apply(
    imageSource: ImageSource[DataState.Identified]): DisplayImageSource =
    imageSource match {
      case works: SourceWorks[DataState.Identified] => DisplayImageSource(works)
    }

  def apply(source: SourceWorks[DataState.Identified]): DisplayImageSource =
    new DisplayImageSource(
      id = source.id.canonicalId,
      title = source.canonicalWork.data.title,
      contributor = source.canonicalWork.data.contributors.headOption
        .map(DisplayContributor(_, includesIdentifiers = false)),
      languages =
        Some(source.canonicalWork.data.languages.map(DisplayLanguage(_))),
      ontologyType = "works"
    )
}
