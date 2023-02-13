package weco.catalogue.internal_model.image

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.WorkData

sealed trait ImageSource {
  val id: IdState.Identified
  val version: Int
}

case class ParentWork(
  id: IdState.Identified,
  data: WorkData[DataState.Identified],
  version: Int
) extends ImageSource
