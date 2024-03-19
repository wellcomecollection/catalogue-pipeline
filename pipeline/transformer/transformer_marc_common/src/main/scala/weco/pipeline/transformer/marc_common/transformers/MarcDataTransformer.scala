package weco.pipeline.transformer.marc_common.transformers

import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.MarcRecord

trait MarcDataTransformer {
  type Output

  def apply(record: MarcRecord): Output
}

trait MarcDataTransformerWithLoggingContext {
  type Output

  def apply(record: MarcRecord)(
    implicit ctx: LoggingContext
  ): Output
}
