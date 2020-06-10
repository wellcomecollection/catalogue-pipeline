package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import cats.data.NonEmptyList

/**
  * Items are merged as follows
  *
  * * Sierra - Single items
  *   * METS works
  *   * Single Miro works, only if the Sierra work has workType Picture/Digital Image/3D Object
  * * Sierra - Multi item
  *   * METS works
  */
object ItemsRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  type FieldData = List[Item[Unminted]]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {
    val items =
      mergeIntoCalmTarget(target, sources)
        .orElse(mergeMetsIntoSingleItemSierraTarget(target, sources)
          .orElse(mergeSingleMiroIntoSingleItemSierraTarget(target, sources)))
        .orElse(mergeIntoMultiItemSierraTarget(target, sources))
        .getOrElse(target.data.items)

    val mergedSources = (
      List(
        mergeIntoCalmTarget,
        mergeMetsIntoSingleItemSierraTarget,
        mergeSingleMiroIntoSingleItemSierraTarget,
        mergeIntoMultiItemSierraTarget)
        .flatMap(_.mergedSources(target, sources)) ++
        findFirstLinkedDigitisedSierraWorkFor(target, sources)
    ).distinct

    FieldMergeResult(
      data = items,
      sources = mergedSources
    )
  }

  /** When there is only 1 Sierra item, we assume that the METS work item
    * is associated with that and merge the locations onto the Sierra item.
    */
  private val mergeMetsIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = singleItemSierra
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {

      // This is safe due to the `singleItemSierra` predicate
      val sierraItem = target.data.items.head

      List(
        sierraItem.copy(
          locations = sierraItem.locations ++ sources.toList
            .flatMap(_.data.items)
            .flatMap(_.locations)
        ))
    }
  }

  /** When there is only 1 Sierra item and 1 Miro item, we assume that the
    * Miro work item is associated with the Sierra item and merge the
    * locations onto the Sierra item.
    *
    * This is restricted to the case that the Sierra work is a Picture/Digital Image/3D Object
    */
  private val mergeSingleMiroIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget
      : WorkPredicate = singleItemSierra and sierraPictureDigitalImageOr3DObject
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
    override val isDefinedForSourceList: Seq[TransformedBaseWork] => Boolean =
      _.count(singleDigitalItemMiroWork) == 1

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {

      // This is safe due to the `singleItemSierra` predicate
      val sierraItem = target.data.items.head

      List(
        sierraItem.copy(
          locations = sierraItem.locations ++ sources.toList
            .flatMap(_.data.items)
            .flatMap(_.locations)
        ))
    }
  }

  /**
    * Miro: we assume the linked Miro work is definitely associated with
    * one of the Sierra items, but unsure of which. We thus don't append it
    * to the Sierra items to avoid certain duplication, and leave the works
    * unmerged.
    *
    * METS: As we are unsure which item the METS work is associated with
    * we add it to the list of items.
    */
  private val mergeIntoMultiItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = multiItemSierra
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.data.items ++ sources.toList.flatMap(_.data.items)
  }

  /**
    * Sierra records are created from the Sierra / Calm harvest.
    * We merge the Sierra item ID into the Calm item.
    *
    * If an item is digitised via METS, it is linked to the Sierra record.
    * We merge that into the Calm item.
    */
  private val mergeIntoCalmTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = singlePhysicalItemCalmWork
    val isDefinedForSource
      : WorkPredicate = singleDigitalItemMetsWork or sierraWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {

      // The calm Work predicate ensures this is safe
      val calmItem = target.data.items.head

      val metsSources = sources.filter(singleDigitalItemMetsWork)
      val metsDigitalLocations =
        metsSources.flatMap(_.data.items.flatMap(_.locations))

      val sierraSources = sources.filter(sierraWork)
      val sierraItemId =
        sierraSources.flatMap(_.data.items.map(_.id)).headOption

      List(
        calmItem.copy(
          locations = calmItem.locations ++ metsDigitalLocations,
          id = sierraItemId.getOrElse(Unidentifiable)
        ))
    }
  }
}
