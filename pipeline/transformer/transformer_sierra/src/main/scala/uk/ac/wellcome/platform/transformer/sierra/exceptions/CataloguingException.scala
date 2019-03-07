package uk.ac.wellcome.platform.transformer.sierra.exceptions

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber

// Thrown if the data has a cataloguing error -- that is, the transformer
// cannot handle it without a change in the source data.
//
// These errors are reported to a separate queue.
//
class CataloguingException(bibId: SierraBibNumber, message: String)
  extends SierraTransformerException(new RuntimeException(s"${bibId.withCheckDigit}: $message"))
