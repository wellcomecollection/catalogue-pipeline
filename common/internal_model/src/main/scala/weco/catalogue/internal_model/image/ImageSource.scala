package weco.catalogue.internal_model.image

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.{Work, WorkData, WorkState}

sealed trait ImageSource {
  val id: IdState.Identified
  val version: Int
}

case class ParentWork(
  id: IdState.Identified,
  data: WorkData[DataState.Identified],
  version: Int,
) extends ImageSource

object ParentWork {

  // These parent works get attached to images.  In general we include all
  // the WorkData fields, in case they're useful later -- but we deliberately
  // omit the ImageData, because it's unnecessary and dramatically increases
  // the size of an image.
  //
  // On one work with a lot of images, the size of the JSON for a single image
  // in the images-initial index went from 3.3MB to 10KB.

  implicit class MergedToParentWork(work: Work[WorkState.Merged]) {
    def toParentWork: ParentWork =
      ParentWork(
        id = IdState
          .Identified(work.state.canonicalId, work.state.sourceIdentifier),
        data = work.data.copy(imageData = Nil),
        version = work.version
      )
  }

  implicit class IdentifiedToParentWork(work: Work[WorkState.Identified]) {
    def toParentWork: ParentWork =
      ParentWork(
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data.copy(imageData = Nil),
        version = work.version
      )
  }
}
