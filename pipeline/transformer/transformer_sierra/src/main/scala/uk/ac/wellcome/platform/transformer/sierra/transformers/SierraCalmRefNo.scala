package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.models.work.internal.Collection
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}

// Field 905 is used for storing the `RefNo` from Calm.
// Field 001 is used for storing the `AltRefNo` from Calm.

// This is temporary.
// When the Calm adapter is done, we will get it from there.
object SierraCalmRefNo extends SierraTransformer with SierraQueryOps {

  type Output = Option[Collection]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) = {
    val refNo = bibData
      .subfieldsWithTag("905" -> "a")
      .contentString

    val altRefNo = bibData
      .varfieldsWithTag("100")
      .contentString

    refNo.map(path => Collection(path = path, label = altRefNo))
  }
}
