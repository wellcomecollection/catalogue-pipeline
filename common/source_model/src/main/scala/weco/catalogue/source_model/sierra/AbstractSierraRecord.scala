package weco.catalogue.source_model.sierra

import weco.catalogue.source_model.sierra.identifiers.TypedSierraRecordNumber

import java.time.Instant

trait AbstractSierraRecord[Id <: TypedSierraRecordNumber] {
  val id: Id
  val data: String
  val modifiedDate: Instant
}
