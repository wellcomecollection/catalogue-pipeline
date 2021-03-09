package weco.catalogue.sierra_linker.dynamo

import org.scanamo.DynamoFormat
import weco.catalogue.sierra_adapter.models.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber
}

object Implicits {
  implicit val formatBibNumber: DynamoFormat[SierraBibNumber] =
    DynamoFormat
      .coercedXmap[SierraBibNumber, String, IllegalArgumentException](
        SierraBibNumber(_),
        _.withoutCheckDigit
      )

  implicit val formatItemNumber: DynamoFormat[SierraItemNumber] =
    DynamoFormat
      .coercedXmap[SierraItemNumber, String, IllegalArgumentException](
        SierraItemNumber(_),
        _.withoutCheckDigit
      )

  implicit val formatHoldingsNumber: DynamoFormat[SierraHoldingsNumber] =
    DynamoFormat
      .coercedXmap[SierraHoldingsNumber, String, IllegalArgumentException](
        SierraHoldingsNumber(_),
        _.withoutCheckDigit
      )
}
