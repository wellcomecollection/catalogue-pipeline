package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}
import weco.catalogue.internal_model.work.{Genre, Subject}

case class ImageQueryableValues(
  @JsonKey("source.subjects.label") sourceSubjectLabels: Seq[String],
  @JsonKey("source.genres.label") sourceGenreLabels: Seq[String],
)

case object ImageQueryableValues {
  def apply(source: ImageSource): ImageQueryableValues =
    source match {
      case ParentWorks(canonicalWork, Some(redirectedWork)) =>
        val subjects
          : Seq[Subject[IdState.Minted]] = canonicalWork.data.subjects ++ redirectedWork.data.subjects

        val genres
          : Seq[Genre[IdState.Minted]] = canonicalWork.data.genres ++ redirectedWork.data.genres

        create(subjects, genres)

      case ParentWorks(canonicalWork, _) =>
        create(canonicalWork.data.subjects, canonicalWork.data.genres)
    }

  private def create(subjects: Seq[Subject[IdState.Minted]],
                     genres: Seq[Genre[IdState.Minted]]): ImageQueryableValues =
    ImageQueryableValues(
      sourceSubjectLabels = subjects.map(_.label),
      sourceGenreLabels = genres.map(_.label)
    )
}
