package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.image.{Image, ImageState}

case class ImageFilterableValues(
  @JsonKey("locations.license.id") locationsLicenseId: List[String],
  @JsonKey(
    "source.contributors.agent.label"
  ) sourceContributorsAgentLabel: List[String],
  @JsonKey(
    "source.contributors.agent.id"
  ) sourceContributorsAgentId: List[String],
  @JsonKey(
    "source.contributors.agent.sourceId"
  ) sourceContributorsAgentSourceId: List[String],
  @JsonKey("source.genres.label") sourceGenresLabel: List[String],
  @JsonKey("source.genres.concepts.id") sourceGenresConceptsId: List[String],
  @JsonKey("source.genres.concepts.sourceId") sourceGenresConceptsSourceId: List[String],
  @JsonKey("source.subjects.label") sourceSubjectsLabel: List[String],
  @JsonKey("source.subjects.concepts.id") sourceSubjectsConceptsId: List[
    String
  ],
  @JsonKey("source.subjects.concepts.sourceId") sourceSubjectsConceptsSourceId: List[
    String
  ],
  @JsonKey(
    "source.production.dates.range.from"
  ) sourceProductionDatesRangeFrom: List[Long]
)

object ImageFilterableValues extends ImageValues {
  import weco.pipeline.ingestor.common.models.ValueTransforms._
  def apply(image: Image[ImageState.Augmented]): ImageFilterableValues =
    new ImageFilterableValues(
      locationsLicenseId = image.locations.flatMap(_.license).map(_.id),
      sourceContributorsAgentLabel = fromParentWork(image.source)(
        _.data.contributors.map(_.agent.label).map(queryableLabel)
      ),
      sourceContributorsAgentId = fromParentWork(image.source)(
        _.data.contributors.map(_.agent.id).canonicalIds
      ),
      sourceContributorsAgentSourceId = fromParentWork(image.source)(
        _.data.contributors.map(_.agent.id).sourceIdentifiers
      ),
      sourceGenresLabel = fromParentWork(image.source)(
        _.data.genres.map(_.label).map(queryableLabel)
      ),
      sourceGenresConceptsId = fromParentWork(image.source)(
        work =>
          genreConcepts(work.data.genres)
            .flatMap(_.id.maybeCanonicalId)
            .map(_.underlying)
      ),
      sourceGenresConceptsSourceId = fromParentWork(image.source)(
        work =>
          genreConcepts(work.data.genres)
            .map(_.id)
            .sourceIdentifiers
      ),
      sourceSubjectsLabel = fromParentWork(image.source)(
        _.data.subjects.map(_.label).map(queryableLabel)
      ),
      sourceSubjectsConceptsId = fromParentWork(image.source)(
        _.data.subjects.map(_.id).canonicalIds
      ),
      sourceSubjectsConceptsSourceId = fromParentWork(image.source)(
        _.data.subjects.map(_.id).sourceIdentifiers
      ),
      sourceProductionDatesRangeFrom = fromParentWork(image.source)(
        _.data.production
          .flatMap(_.dates)
          .flatMap(_.range)
          .map(_.from.toEpochMilli)
      )
    )
}
