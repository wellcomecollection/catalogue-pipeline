package weco.catalogue.sierra_indexer.models

import io.circe.Json

case class IndexedFixedField(
  parent: Parent,
  code: String,
  fixedField: Json
) {
  def id: String = s"${parent.id}-$code"
}
