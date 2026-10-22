package weco.pipeline.merger.models

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.rules.{ItemsRule, WorkPredicates}

/*
 * MergeResult holds the resultant target after all fields have been merged,
 * and the images that were created in the process
 */
case class MergeResult(
  mergedTarget: Work.Visible[Identified],
  imageDataWithSources: Seq[ImageDataWithSource]
)

object TargetOnlyMergeResult extends WorkMergingOps {
  import weco.catalogue.internal_model.image.ParentWork._
  def apply(target: Work.Visible[Identified]): MergeResult = {
    val items = ItemsRule.unmergedItems(target)
    MergeResult(
      mergedTarget = target
        .mapData(_.copy[DataState.Identified](items = items))
        .withItemsInInternalWorks(items),
      imageDataWithSources = standaloneImages(target).map {
        image =>
          ImageDataWithSource(
            imageData = image,
            source = target.toParentWork
          )
      }
    )
  }

  private def standaloneImages(
    target: Work.Visible[Identified]
  ): List[ImageData[IdState.Identified]] =
    if (WorkPredicates.singleDigitalItemMiroWork(target)) target.data.imageData
    else Nil

}
