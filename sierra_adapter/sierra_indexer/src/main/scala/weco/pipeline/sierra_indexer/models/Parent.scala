package weco.pipeline.sierra_indexer.models

import weco.sierra.models.identifiers.{
  SierraRecordTypes,
  TypedSierraRecordNumber
}

case class Parent(
  recordType: SierraRecordTypes.Value,
  id: TypedSierraRecordNumber,
  idWithCheckDigit: String
)

case object Parent {
  def apply(id: TypedSierraRecordNumber): Parent =
    Parent(
      recordType = id.recordType,
      id = id,
      idWithCheckDigit = id.withCheckDigit
    )
}
