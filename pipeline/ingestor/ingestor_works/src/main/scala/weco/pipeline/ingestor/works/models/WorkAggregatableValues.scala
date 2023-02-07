package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.work.{Availability, WorkData}
import weco.pipeline.ingestor.common.models.AggregatableValues

case class WorkAggregatableValues(
  @JsonKey("workType") workTypes: List[String],
  @JsonKey("genres.label") genres: List[String],
  @JsonKey("production.dates") productionDates: List[String],
  @JsonKey("subjects.label") subjects: List[String],
  @JsonKey("languages") languages: List[String],
  @JsonKey("contributors.agent.label") contributors: List[String],
  @JsonKey("items.locations.license") itemLicenses: List[String],
  @JsonKey("availabilities") availabilities: List[String]
)

case object WorkAggregatableValues extends AggregatableValues {
  def apply(
    workData: WorkData[DataState.Identified],
    availabilities: Set[Availability]
  ): WorkAggregatableValues =
    WorkAggregatableValues(
      workTypes = workData.workTypeAggregatableValues,
      genres = workData.genreAggregatableValues,
      productionDates = workData.productionDateAggregatableValues,
      subjects = workData.subjectLabelAggregatableValues,
      languages = workData.languageAggregatableValues,
      contributors = workData.contributorAggregatableValues,
      itemLicenses = workData.licenseAggregatableValues,
      availabilities = availabilities.aggregatableValues
    )
}
