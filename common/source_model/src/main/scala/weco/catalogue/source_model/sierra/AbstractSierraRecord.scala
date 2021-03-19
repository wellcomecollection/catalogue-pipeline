package weco.catalogue.source_model.sierra

import java.time.Instant

trait AbstractSierraRecord[Id <: TypedSierraRecordNumber] {
  val id: Id
  val data: String
  val modifiedDate: Instant
}
