package weco.pipeline.merger.rules.tei

import cats.data.NonEmptyList
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.models.FieldMergeResult
import weco.pipeline.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import weco.pipeline.merger.rules.BaseItemsRule
import weco.pipeline.merger.rules.WorkPredicates._

/**
  * Items are merged as follows
  *
  * * Sierra - Single items or zero items
  *   * METS works
  *   * Single Miro works, only if the Sierra work has format Picture/Digital Image/3D Object
  * * Sierra - Multi item
  *   * METS works
  */
object TeiItemsRule extends BaseItemsRule {

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] = {
    val items =
      mergeIntoTeiTarget(target, sources)
        .orElse(mergeIntoCalmTarget(target, sources))
        .orElse(mergeMetsIntoSierraTarget(target, sources))
        .orElse(
          mergeSingleMiroIntoSingleOrZeroItemSierraTarget(target, sources)
        )
        .orElse(mergeDigitalIntoPhysicalSierraTarget(target, sources))
        .getOrElse(target.data.items)

    val mergedSources = (
      List(
        mergeIntoTeiTarget,
        mergeIntoCalmTarget,
        mergeMetsIntoSierraTarget,
        mergeSingleMiroIntoSingleOrZeroItemSierraTarget,
        mergeDigitalIntoPhysicalSierraTarget
      ).flatMap { rule =>
        rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)
        ++ knownDuplicateSources(target, sources)
    ).distinct

    FieldMergeResult(
      data = items,
      sources = mergedSources
    )
  }

  private val mergeIntoTeiTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = teiWork
    val isDefinedForSource: WorkPredicate =
      singlePhysicalItemCalmWork or
        singleDigitalItemMetsWork or
        singleDigitalItemMiroWork or
        sierraWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      sources.map(_.data.items).toList.flatten
  }

}
