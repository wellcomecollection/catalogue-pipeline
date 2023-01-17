package weco.pipeline.sierra_linker.dynamo

import org.scanamo.DynamoFormat
import weco.sierra.models.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber
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

  implicit val formatOrderNumber: DynamoFormat[SierraOrderNumber] =
    DynamoFormat
      .coercedXmap[SierraOrderNumber, String, IllegalArgumentException](
        SierraOrderNumber(_),
        _.withoutCheckDigit
      )
}
