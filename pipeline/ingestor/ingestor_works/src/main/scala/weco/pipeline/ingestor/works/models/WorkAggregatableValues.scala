package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.pipeline.ingestor.common.models.{
  AggregatableIdLabel,
  AggregatableValues
}

case class WorkAggregatableValues(
  @JsonKey("workType") workTypes: List[AggregatableIdLabel],
  @JsonKey("genres") genres: List[AggregatableIdLabel],
  @JsonKey("production.dates") productionDates: List[AggregatableIdLabel],
  @JsonKey("subjects") subjects: List[AggregatableIdLabel],
  @JsonKey("languages") languages: List[AggregatableIdLabel],
  @JsonKey("contributors.agent") contributors: List[AggregatableIdLabel],
  @JsonKey("items.locations.license") itemLicenses: List[AggregatableIdLabel],
  @JsonKey("availabilities") availabilities: List[AggregatableIdLabel]
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
