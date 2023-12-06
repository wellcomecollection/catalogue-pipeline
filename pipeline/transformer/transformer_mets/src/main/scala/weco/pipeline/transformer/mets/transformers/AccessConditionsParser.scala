package weco.pipeline.transformer.mets.transformers

import weco.pipeline.transformer.result.Result

trait AccessConditionsParser {
  def parse: Result[MetsAccessConditions]
}
