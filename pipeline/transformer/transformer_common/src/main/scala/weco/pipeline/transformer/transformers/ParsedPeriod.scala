package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.text.TextNormalisation._
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{InstantRange, Period}
import weco.catalogue.internal_model.parse.parsers.DateParser

object ParsedPeriod {
  def apply(label: String): Period[IdState.Unidentifiable.type] = {
    val normalisedLabel = label.trimTrailingPeriod
    Period(
      IdState.Unidentifiable,
      normalisedLabel,
      InstantRange.parse(normalisedLabel))
  }
}
