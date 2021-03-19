package weco.catalogue.sierra_indexer.models

import weco.catalogue.source_model.sierra.{
  SierraRecordTypes,
  TypedSierraRecordNumber
}

case class Parent(
  recordType: SierraRecordTypes.Value,
  id: TypedSierraRecordNumber
)
