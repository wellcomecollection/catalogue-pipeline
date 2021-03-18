package uk.ac.wellcome.models.work.internal

import weco.catalogue.internal_model.DerivedDataCommon

case class DerivedWorkData(
  contributorAgents: List[String] = Nil,
)

object DerivedWorkData extends DerivedDataCommon {
  def apply(data: WorkData[_]): DerivedWorkData =
    DerivedWorkData(
      contributorAgents = contributorAgentLabels(data.contributors)
    )

  def none: DerivedWorkData =
    DerivedWorkData(
      contributorAgents = Nil
    )
}


