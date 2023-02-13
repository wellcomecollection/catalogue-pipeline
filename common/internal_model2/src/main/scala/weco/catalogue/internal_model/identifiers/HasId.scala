package weco.catalogue.internal_model.identifiers

/** A trait assigned to all objects that contain some ID value. */
trait HasId[+T] {
  val id: T
}
