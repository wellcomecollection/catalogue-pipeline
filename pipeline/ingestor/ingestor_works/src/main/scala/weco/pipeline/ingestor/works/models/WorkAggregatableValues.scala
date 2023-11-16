package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.{
  Work,
  WorkState
}
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
    work: Work.Visible[WorkState.Denormalised]
  ): WorkAggregatableValues =
    WorkAggregatableValues(
      workTypes = work.data.workTypeAggregatableValues,
      genres = work.data.genreAggregatableValues,
      productionDates = work.data.productionDateAggregatableValues,
      subjects = work.data.subjectLabelAggregatableValues,
      languages = work.data.languageAggregatableValues,
      contributors = work.data.contributorAggregatableValues,
      itemLicenses = work.data.licenseAggregatableValues,
      availabilities = work.state.availabilities.aggregatableValues
    )
}
