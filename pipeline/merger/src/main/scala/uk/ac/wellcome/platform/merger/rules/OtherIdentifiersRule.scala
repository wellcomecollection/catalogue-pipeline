package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import cats.implicits._

/**
  * This rule replicates the matching functionality within the ItemsRule.
  * If any of those Rules are matched, the IDs are added to the target.
  * IDs are then deduped in the merge.
  */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {

    // We have to do the check if there a digitised linked work before the rule
    // as the condition relies on the target and sources.
    val digitisedSierraIds =
      findFirstLinkedDigitisedSierraWorkFor(target, sources)
        .map(_.sourceIdentifier)
        .map(mergeDigitalIntoPhysicalSierraTarget)
        .flatMap(rule => rule(target, sources))
        .getOrElse(Nil)

    val singleItemSierraIds =
      mergeMetsIntoSingleItemSierraTarget(target, sources) |+|
        mergeSingleMiroIntoPictureSierraTarget(target, sources)

    val ids =
      (mergeIntoCalmTarget(target, sources)
        .orElse(singleItemSierraIds)
        .orElse(mergeIntoMultiItemSierraTarget(target, sources))
        .getOrElse(target.otherIdentifiers) ++ digitisedSierraIds).distinct

    val mergedSources = (
      List(
        mergeIntoCalmTarget,
        mergeMetsIntoSingleItemSierraTarget,
        mergeSingleMiroIntoPictureSierraTarget,
        mergeIntoMultiItemSierraTarget
      ).flatMap { rule =>
        rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)
    ).distinct

    FieldMergeResult(
      data = ids,
      sources = mergedSources
    )
  }

  private val mergeMetsIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = singleItemSierra
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers
  }

  private val mergeSingleMiroIntoPictureSierraTarget = new PartialRule {
    val isDefinedForTarget
      : WorkPredicate = singleItemSierra and sierraPictureDigitalImageOr3DObject
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
    override val isDefinedForSourceList: Seq[TransformedBaseWork] => Boolean =
      _.count(singleDigitalItemMiroWork) == 1

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
  }

  private val mergeIntoMultiItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = multiItemSierra
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers
  }

  private val mergeIntoCalmTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = singlePhysicalItemCalmWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or sierraWork or singleDigitalItemMiroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
  }

  private val mergeDigitalIntoPhysicalSierraTarget =
    (linkedSierraSourceId: SourceIdentifier) =>
      new PartialRule {
        val isDefinedForTarget: WorkPredicate = physicalSierra
        val isDefinedForSource: WorkPredicate =
          sierraWorkWithId(linkedSierraSourceId)

        def rule(target: UnidentifiedWork,
                 sources: NonEmptyList[TransformedBaseWork]): FieldData =
          target.otherIdentifiers ++ sources.toList.flatMap(_.identifiers)
    }
}
