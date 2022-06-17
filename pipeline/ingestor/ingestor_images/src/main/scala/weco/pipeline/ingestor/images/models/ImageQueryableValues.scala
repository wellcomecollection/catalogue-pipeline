package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.{ImageSource, ParentWork}
import weco.catalogue.internal_model.work.{Genre, Subject}

case class ImageQueryableValues(
  @JsonKey("source.subjects.label") sourceSubjectLabels: Seq[String],
  @JsonKey("source.genres.label") sourceGenreLabels: Seq[String],
)

case object ImageQueryableValues {
  def apply(source: ImageSource): ImageQueryableValues =
    source match {
      case ParentWork(_, workData, _) =>
        create(workData.subjects, workData.genres)
    }

  private def create(subjects: Seq[Subject[IdState.Minted]],
                     genres: Seq[Genre[IdState.Minted]]): ImageQueryableValues =
    ImageQueryableValues(
      sourceSubjectLabels = subjects.map(_.label),
      sourceGenreLabels = genres.map(_.label)
    )
}
