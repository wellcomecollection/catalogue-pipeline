package weco.pipeline.transformer.marc_common.transformers

import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcRecord}

/*
 * A MarcDataTransformer finds the appropriate field(s) within a
 * MarcRecord, and transforms them into the target output.
 */
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

/*
 * A MarcFieldTransformer transforms a given MarcField into the target
 * output.
 *
 * This allows for fields that that may have subtly different final outputs
 * (as governed by a MarcDataTransformer) depending on which field it is.
 *
 * For example, an Organisation or a Person may be a subject or a contributor.
 * The FieldTransformer can generate a Person, regardless of which it is
 * while the corresponding DataTransformer sets the Subjectness or Contributorness
 * of it.
 *  */
trait MarcFieldTransformer {
  type Output

  def apply(field: MarcField): Output
}
