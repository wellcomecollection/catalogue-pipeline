package uk.ac.wellcome.models.work.internal

/** Container type for IdState types, that is used by WorkData / ImageData
  * with two associated types:
  *
  * - Id (references an ID type, always with a source identifier)
  * - MaybeId (references an ID type, potentially with a source identifier)
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
