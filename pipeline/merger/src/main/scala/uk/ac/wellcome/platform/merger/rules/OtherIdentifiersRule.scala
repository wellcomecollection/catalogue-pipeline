package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}
import uk.ac.wellcome.platform.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import cats.implicits._

/**
  * Identifiers are merged as follows:
  *
  * - All source identifiers are merged into Calm works
  * - Miro identifiers are merged into single or zero item Sierra works
  * - Sierra works with linked digitised Sierra works have the first
  *   of these linked IDs merged into them
  * - METS identifiers are not merged as they are not useful
  */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {
    val ids = (
      mergeDigitalIntoPhysicalSierraTarget(target, sources) |+|
        mergeIntoCalmTarget(target, sources)
          .orElse(mergeMiroIntoSingleOrZeroItemSierraTarget(target, sources))
    ).getOrElse(target.otherIdentifiers).distinct

    val mergedSources = sources.filter { source =>
      List(
        mergeIntoCalmTarget,
        mergeMiroIntoSingleOrZeroItemSierraTarget
      ).exists(_(target, source).isDefined)
    } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)

    FieldMergeResult(
      data = ids,
      sources = mergedSources.distinct
    )
  }

  private val mergeMiroIntoSingleOrZeroItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate =
      WorkPredicates.singleItemSierra or WorkPredicates.zeroItemSierra
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMiroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
  }

  private val mergeIntoCalmTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate =
      WorkPredicates.singlePhysicalItemCalmWork
    val isDefinedForSource
      : WorkPredicate = WorkPredicates.singleDigitalItemMetsWork or WorkPredicates.sierraWork or WorkPredicates.singleDigitalItemMiroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
  }

  private val mergeDigitalIntoPhysicalSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.physicalSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.sierraWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      findFirstLinkedDigitisedSierraWorkFor(target, sources.toList)
        .map(target.otherIdentifiers ++ _.identifiers)
        .getOrElse(Nil)
  }
}
