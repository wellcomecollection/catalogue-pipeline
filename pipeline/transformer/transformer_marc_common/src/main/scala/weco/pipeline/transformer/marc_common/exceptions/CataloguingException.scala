package weco.pipeline.transformer.marc_common.exceptions

import weco.pipeline.transformer.marc_common.models.MarcRecord

// Thrown if the data has a cataloguing error -- that is, the transformer
// cannot handle it without a change in the source data.
//
// These errors are reported to a separate queue.
//
class CataloguingException(record: MarcRecord, message: String)
    extends MarcTransformerException(
      new RuntimeException(
        s"Problem in the MARC data for $record: $message"
      )
    )

case object CataloguingException {
  def apply(record: MarcRecord, message: String): CataloguingException =
    new CataloguingException(record, message)
}
