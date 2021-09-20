package weco.catalogue.internal_model.identifiers

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
