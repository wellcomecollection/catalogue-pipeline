package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
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
  import WorkPredicates._

  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {
    val ids = (
      mergeDigitalIntoPhysicalSierraTarget(target, sources) |+|
        mergeIntoCalmTarget(target, sources)
          .orElse(
            mergeSingleMiroIntoSingleOrZeroItemSierraTarget(target, sources))
    ).getOrElse(target.otherIdentifiers).distinct

    val mergedSources = (
      List(
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

  private val mergeSingleMiroIntoSingleOrZeroItemSierraTarget =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate =
        (singleItemSierra or zeroItemSierra) and sierraPictureDigitalImageOr3DObject
      val isDefinedForSource: WorkPredicate =
        singleDigitalItemMiroWork
      override val isDefinedForSourceList: Seq[TransformedBaseWork] => Boolean =
        _.count(singleDigitalItemMiroWork) == 1

      def rule(target: UnidentifiedWork,
               sources: NonEmptyList[TransformedBaseWork]): FieldData =
        target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
    }

  private val mergeIntoCalmTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = singlePhysicalItemCalmWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or sierraWork or singleDigitalItemMiroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.otherIdentifiers ++ sources.toList.map(_.sourceIdentifier)
  }

  private val mergeDigitalIntoPhysicalSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = physicalSierra
    val isDefinedForSource: WorkPredicate = sierraWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      findFirstLinkedDigitisedSierraWorkFor(target, sources.toList)
        .map(target.otherIdentifiers ++ _.identifiers)
        .getOrElse(Nil)
  }
}
