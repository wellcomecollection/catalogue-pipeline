package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.{Work, WorkState}

case class WorkFilterableValues(
  @JsonKey("format.id") formatId: Option[String],
  @JsonKey("workType") workType: String,
  @JsonKey("production.dates.range.from") productionDatesRangeFrom: List[Long],
  @JsonKey("languages.id") languagesId: List[String],
  @JsonKey("genres.label") genresLabel: List[String],
  @JsonKey("genres.concepts.id") genresConceptsId: List[String],
  @JsonKey("genres.concepts.sourceIdentifier") genresConceptsSourceIdentifier: List[String],
  @JsonKey("subjects.label") subjectsLabel: List[String],
  @JsonKey("subjects.concepts.id") subjectsConceptsId: List[String],
  @JsonKey("subjects.concepts.sourceIdentifier") subjectsConceptsSourceIdentifier: List[String],
  @JsonKey("contributors.agent.label") contributorsAgentLabel: List[String],
  @JsonKey("contributors.agent.id") contributorsAgentId: List[String],
  @JsonKey("contributors.agent.sourceIdentifier") contributorsAgentSourceIdentifier: List[
    String
  ],
  @JsonKey("identifiers.value") identifiersValue: List[String],
  @JsonKey("items.locations.license.id") itemsLocationsLicenseId: List[String],
  @JsonKey(
    "items.locations.accessConditions.status.id"
  ) itemsLocationsAccessConditionsStatusId: List[String],
  @JsonKey("items.id") itemsId: List[String],
  @JsonKey("items.identifiers.value") itemsIdentifiersValue: List[String],
  @JsonKey(
    "items.locations.locationType.id"
  ) itemsLocationsLocationTypeId: List[String],
  @JsonKey("partOf.id") partOfId: List[String],
  @JsonKey("partOf.title") partOfTitle: List[String],
  @JsonKey("availabilities.id") availabilitiesId: List[String]
)

object WorkFilterableValues {
  import weco.pipeline.ingestor.common.models.ValueTransforms._
  def apply(work: Work.Visible[WorkState.Denormalised]): WorkFilterableValues =
    new WorkFilterableValues(
      formatId = work.data.format.map(_.id),
      workType = work.data.workType.toString,
      productionDatesRangeFrom = for {
        production <- work.data.production
        date <- production.dates
        range <- date.range
      } yield range.from.toEpochMilli,
      languagesId = work.data.languages.map(_.id),
      genresLabel = work.data.genres.map(_.label).map(queryableLabel),
      genresConceptsId = for {
        concept <- genreConcepts(work.data.genres)
        id <- concept.id.maybeCanonicalId
      } yield id.underlying,
      genresConceptsSourceIdentifier =
        genreConcepts(work.data.genres).map(_.id).sourceIdentifiers,
      subjectsLabel = work.data.subjects.map(_.label).map(queryableLabel),
      subjectsConceptsId = work.data.subjects.map(_.id).canonicalIds,
      subjectsConceptsSourceIdentifier = work.data.subjects.map(_.id).sourceIdentifiers,
      contributorsAgentLabel =
        work.data.contributors.map(_.agent.label).map(queryableLabel),
      contributorsAgentId = work.data.contributors.map(_.agent.id).canonicalIds,
      contributorsAgentSourceIdentifier =
        work.data.contributors.map(_.agent.id).sourceIdentifiers,
      identifiersValue =
        (work.sourceIdentifier +: work.data.otherIdentifiers).map(_.value),
      itemsLocationsLicenseId =
        work.data.items.flatMap(_.locations).flatMap(_.license).map(_.id),
      itemsLocationsAccessConditionsStatusId = for {
        item <- work.data.items
        location <- item.locations
        accessCondition <- location.accessConditions
        status <- accessCondition.status
      } yield status.id,
      itemsId = work.data.items.map(_.id).canonicalIds,
      itemsIdentifiersValue =
        work.data.items.flatMap(_.id.allSourceIdentifiers).map(_.value),
      itemsLocationsLocationTypeId =
        work.data.items.flatMap(_.locations).map(_.locationType.id),
      partOfId = work.state.relations.ancestors.flatMap(_.id).map(_.underlying),
      partOfTitle = work.state.relations.ancestors.flatMap(_.title),
      availabilitiesId = work.state.availabilities.map(_.id).toList
    )
}
