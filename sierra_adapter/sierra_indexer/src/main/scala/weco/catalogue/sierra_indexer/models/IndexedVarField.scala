package weco.catalogue.sierra_indexer.models

import io.circe.Json

case class IndexedVarField(
  parent: Parent,
  position: Int,
  varField: Json
) {
  def id: String = s"${parent.id}-$position"
}
