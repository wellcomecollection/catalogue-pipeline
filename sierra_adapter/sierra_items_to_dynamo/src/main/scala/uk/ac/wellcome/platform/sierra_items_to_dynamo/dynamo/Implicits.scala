package uk.ac.wellcome.platform.sierra_items_to_dynamo.dynamo

import org.scanamo.DynamoFormat
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraItemNumber}

object Implicits {
  implicit val formatBibNumber: DynamoFormat[SierraBibNumber] =
    DynamoFormat
      .coercedXmap[SierraBibNumber, String, IllegalArgumentException](
        SierraBibNumber,
        _.withoutCheckDigit
      )

  implicit val formatItemNumber: DynamoFormat[SierraItemNumber] =
    DynamoFormat
      .coercedXmap[SierraItemNumber, String, IllegalArgumentException](
        SierraItemNumber,
        _.withoutCheckDigit
      )
}
