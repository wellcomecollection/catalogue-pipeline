package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}
import uk.ac.wellcome.platform.merger.models.Sources.SourcesOps
import cats.implicits._

/**
  * This rule replicates the matching functionality within the ItemsRule.
  * If any of those Rules are matched, the IDs are added to the target.
  * IDs are then deduped in the merge.
  */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {

    // We have to do the check if there a digitised linked work before the rule
    // as the condition relies on the target and sources.
    val digitisedSierraIds = sources
      .findFirstLinkedDigitisedSierraWorkFor(target)
      .map(_.sourceIdentifier)
      .map(mergeDigitalIntoPhysicalSierraTarget)
      .flatMap(rule => rule(target, sources))
      .getOrElse(Nil)

    val rules =
      List(
        mergeIntoCalmTarget,
        mergeMetsIntoSingleItemSierraTarget,
        mergeMiroIntoSingleItemSierraTarget,
        mergeIntoMultiItemSierraTarget)

    val singleItemSierraIds =
      mergeMetsIntoSingleItemSierraTarget(target, sources) |+|
        mergeMiroIntoSingleItemSierraTarget(target, sources)

    val ids =
      (mergeIntoCalmTarget(target, sources)
        .orElse(singleItemSierraIds)
        .orElse(mergeIntoMultiItemSierraTarget(target, sources))
        .getOrElse(target.otherIdentifiers) ++ digitisedSierraIds).distinct

    val mergedSources = sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    } ++ sources.findFirstLinkedDigitisedSierraWorkFor(target)

    FieldMergeResult(
      data = ids,
      sources = mergedSources.distinct
    )
  }

  private val mergeMetsIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers
  }

  private val mergeMiroIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMiroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
  }

  private val mergeIntoMultiItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.multiItemSierra
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers
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

  private val mergeDigitalIntoPhysicalSierraTarget =
    (linkedSierraSourceId: SourceIdentifier) =>
      new PartialRule {
        val isDefinedForTarget: WorkPredicate = WorkPredicates.physicalSierra
        val isDefinedForSource: WorkPredicate =
          WorkPredicates.sierraWorkWithId(linkedSierraSourceId)

        def rule(target: UnidentifiedWork,
                 sources: NonEmptyList[TransformedBaseWork]): FieldData =
          target.otherIdentifiers ++ sources.toList.flatMap(_.identifiers)
    }
}
