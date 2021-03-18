package weco.catalogue.internal_model

import uk.ac.wellcome.models.work.internal.Contributor

trait DerivedDataCommon {
  protected def contributorAgentLabels(
    contributors: List[Contributor[_]]): List[String] =
    contributors.map(_.agent.typedLabel)
}
