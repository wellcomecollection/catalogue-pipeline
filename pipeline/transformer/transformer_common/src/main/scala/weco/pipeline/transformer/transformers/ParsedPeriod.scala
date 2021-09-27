package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.text.TextNormalisation._
import weco.catalogue.internal_model.work.Period
import weco.pipeline.transformer.parse.PeriodParser

object ParsedPeriod {
  def apply(label: String): Period[IdState.Unidentifiable.type] = {
    val normalisedLabel = label.trimTrailingPeriod
    Period(
      id = IdState.Unidentifiable,
      label = normalisedLabel,
      range = PeriodParser(normalisedLabel)
    )
  }
}
