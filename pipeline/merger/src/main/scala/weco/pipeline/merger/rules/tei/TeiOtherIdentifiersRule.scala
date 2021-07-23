package weco.pipeline.merger.rules.tei

import cats.data.NonEmptyList
import cats.implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.models.FieldMergeResult
import weco.pipeline.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import weco.pipeline.merger.rules.BaseOtherIdentifiersRule
import weco.pipeline.merger.rules.WorkPredicates._

object TeiOtherIdentifiersRule extends BaseOtherIdentifiersRule{
  override def merge(
                      target: Work.Visible[Identified],
                      sources: Seq[Work[Identified]]): FieldMergeResult[FieldData] = {
    val ids = (
      mergeDigitalIntoPhysicalSierraTarget(target, sources) |+|
        mergeIntoTeiTarget(target, sources)
        .orElse(mergeIntoCalmTarget(target, sources))
          .orElse(
            mergeSingleMiroIntoSingleOrZeroItemSierraTarget(target, sources))
      ).getOrElse(target.data.otherIdentifiers).distinct

    val mergedSources = (
      List(
        mergeIntoTeiTarget,
        mergeIntoCalmTarget,
        mergeSingleMiroIntoSingleOrZeroItemSierraTarget
      ).flatMap { rule =>
        rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)
      ).distinct

    FieldMergeResult(
      data = ids,
      sources = mergedSources
    )
  }


  private val mergeIntoTeiTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = teiWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or sierraWork or singleDigitalItemMiroWork or singlePhysicalItemCalmWork

    def rule(target: Work.Visible[Identified],
             sources: NonEmptyList[Work[Identified]]): FieldData =
      target.data.otherIdentifiers ++ sources.toList.flatMap(
        getAllowedIdentifiersFromSource)
  }
}
