package weco.catalogue.internal_model.identifiers

/** Container type for IdState types, that is used by WorkData / ImageData
  * with three associated types:
  *
  * - Id (references an ID type, always with a source identifier)
  * - MaybeId (references an ID type, potentially with a source identifier)
  * - WorkImage (references the type of the image inside the work data)
  */
sealed trait DataState {
  type Id
  type MaybeId
}

object DataState {
  case class Unidentified() extends DataState {
    type Id = IdState.Identifiable
    type MaybeId = IdState.Unminted
  }

  case class Identified() extends DataState {
    type Id = IdState.Identified
    type MaybeId = IdState.Minted
  }
}
