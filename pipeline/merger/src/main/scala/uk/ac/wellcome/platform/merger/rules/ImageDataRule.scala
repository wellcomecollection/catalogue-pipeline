package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.Work

object ImageDataRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[ImageData[IdState.Identified]]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]] = Nil
  ): FieldMergeResult[FieldData] = {
    // We merge images into Sierra targets, regardless of whether this is the principal
    // target of the graph we're currently merging (ie if there's a Calm target, it's ignored)
    TargetPrecedence
      .targetSatisfying(sierraWork)(
        target +: sources.collect(TargetPrecedence.visibleWork)
      )
      .map { sierraTarget =>
        FieldMergeResult(
          data = getMetsPictureAndEphemeraImages(sierraTarget, sources)
            .getOrElse(Nil) ++
            getPairedMiroImages(sierraTarget, sources).getOrElse(Nil),
          sources = List(
            getMetsPictureAndEphemeraImages,
            getPairedMiroImages
          ).flatMap(_.mergedSources(sierraTarget, sources))
        )
      }
      .getOrElse(FieldMergeResult(data = Nil, sources = Nil))
  }

  private lazy val getMetsPictureAndEphemeraImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraPictureOrEphemera
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork
  }

  // In future we may change `digaids` to `digmiro` for all works
  // where we know that the Miro and METS images are identical
  private lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate =
      sierraWork and not(sierraDigaids)
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  trait FlatImageMergeRule extends PartialRule {
    final override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): List[ImageData[IdState.Identified]] =
      (target :: sources).toList.flatMap(_.data.imageData)
  }

}
