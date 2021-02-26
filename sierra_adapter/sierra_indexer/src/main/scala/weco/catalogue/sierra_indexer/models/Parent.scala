package weco.catalogue.sierra_indexer.models

import weco.catalogue.sierra_adapter.models.{SierraRecordTypes, TypedSierraRecordNumber}

case class Parent(
  recordType: SierraRecordTypes.Value,
  id: TypedSierraRecordNumber
)

