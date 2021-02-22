package weco.catalogue.sierra_adapter.dynamo

import org.scanamo.DynamoFormat
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraHoldingsNumber, SierraItemNumber}

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

  implicit val formatHoldingsNumber: DynamoFormat[SierraHoldingsNumber] =
    DynamoFormat
      .coercedXmap[SierraHoldingsNumber, String, IllegalArgumentException](
        SierraHoldingsNumber,
        _.withoutCheckDigit
      )
}
