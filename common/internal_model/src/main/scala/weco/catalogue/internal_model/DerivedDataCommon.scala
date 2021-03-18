package weco.catalogue.internal_model

import weco.catalogue.internal_model.work.Contributor

trait DerivedDataCommon {
  protected def contributorAgentLabels(
    contributors: List[Contributor[_]]): List[String] =
    contributors.map(_.agent.typedLabel)
}
