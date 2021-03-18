package uk.ac.wellcome.models.work.internal

case class DerivedWorkData(
  contributorAgents: List[String] = Nil,
)

trait DerivedDataCommon {
  protected def contributorAgentLabels(
    contributors: List[Contributor[_]]): List[String] =
    contributors.map(_.agent.typedLabel)
}

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


