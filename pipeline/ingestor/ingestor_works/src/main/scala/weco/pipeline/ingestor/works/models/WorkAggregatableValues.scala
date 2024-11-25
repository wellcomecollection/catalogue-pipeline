package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.pipeline.ingestor.common.models.{
  AggregatableField,
  AggregatableValues
}

case class WorkAggregatableValues(
  @JsonKey("workType") workTypes: List[AggregatableField],
  @JsonKey("genres") genres: List[AggregatableField],
  @JsonKey("production.dates") productionDates: List[AggregatableField],
  @JsonKey("subjects") subjects: List[AggregatableField],
  @JsonKey("languages") languages: List[AggregatableField],
  @JsonKey("contributors.agent") contributors: List[AggregatableField],
  @JsonKey("items.locations.license") itemLicenses: List[AggregatableField],
  @JsonKey("availabilities") availabilities: List[AggregatableField]
)

case object WorkAggregatableValues extends AggregatableValues {
  def apply(
    work: Work.Visible[WorkState.Denormalised]
  ): WorkAggregatableValues =
    WorkAggregatableValues(
      workTypes = work.data.workTypeAggregatableValues,
      genres = work.data.genreAggregatableValues,
      productionDates = work.data.productionDateAggregatableValues,
      subjects = work.data.subjectAggregatableValues,
      languages = work.data.languageAggregatableValues,
      contributors = work.data.contributorAggregatableValues,
      itemLicenses = work.data.licenseAggregatableValues,
      availabilities = work.state.availabilities.aggregatableValues
    )
}
