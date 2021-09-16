package weco.catalogue.internal_model.identifiers

/** A trait assigned to all objects that contain some ID value.
  *
  * This trait isn't used in the pipeline library -- instead, it's used by
  * one of the serialisers in the display model tests in the catalogue-api repo.
  *
  * See https://github.com/wellcomecollection/catalogue-api/blob/f7ce5ae679b90c61ae064a0c6f9517f787c5e886/common/display/src/test/scala/weco/catalogue/display_model/models/DisplaySerialisationTestBase.scala#L115-L121
  *
  */
trait HasId[+T] {
  val id: T
}
