package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}
import uk.ac.wellcome.platform.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import cats.data.NonEmptyList

/**
  * Items are merged as follows
  *
  * * Sierra - Single items or zero items
  *   * METS works
  *   * Miro works
  * * Sierra - Multi item
  *   * METS works
  */
object ItemsRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[Item[Unminted]]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {
    val rules =
      List(
        mergeIntoCalmTarget,
        mergeMetsIntoSierraTarget,
        mergeMiroIntoSingleOrZeroItemSierraTarget,
      )

    val items =
      mergeIntoCalmTarget(target, sources)
        .orElse(mergeMetsIntoSierraTarget(target, sources))
        .orElse(mergeMiroIntoSingleOrZeroItemSierraTarget(target, sources))
        .getOrElse(target.data.items)

    val mergedSources = sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)

    FieldMergeResult(
      data = items,
      sources = mergedSources
    )
  }

  /** When there is only 1 Sierra item, we assume that the METS work item
    * is associated with that and merge the locations onto the Sierra item.
    *
    * Otherwise (including if there are no Sierra items) we append the METS
    * item to the Sierra items
    */
  private val mergeMetsIntoSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMetsWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.data.items match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations ++ sources.toList
                .flatMap(_.data.items)
                .flatMap(_.locations)
            )
          )
        case _ => target.data.items ++ sources.toList.flatMap(_.data.items)
      }
  }

  /** When there is only 1 Sierra item, we assume that the Miro work item
    * is associated with that and merge the locations onto the Sierra item.
    *
    * When there are no Sierra items, we add the Miro item.
    *
    * When there are multiple Sierra items, we assume the linked Miro work
    * is definitely associated with * one of them, but unsure of which.
    * Thus we don't append it to the Sierra items to avoid certain duplication,
    * and leave the works unmerged.
    */
  private val mergeMiroIntoSingleOrZeroItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate =
      WorkPredicates.singleItemSierra or WorkPredicates.zeroItemSierra
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMiroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.data.items match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations ++ sources.toList
                .flatMap(_.data.items)
                .flatMap(_.locations)
            )
          )
        case _ => sources.toList.flatMap(_.data.items)
      }
  }

  /**
    * Sierra records are created from the Sierra / Calm harvest.
    * We merge the Sierra item ID into the Calm item.
    *
    * If an item is digitised via METS, it is linked to the Sierra record.
    * We merge that into the Calm item.
    */
  private val mergeIntoCalmTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate =
      WorkPredicates.singlePhysicalItemCalmWork
    val isDefinedForSource
      : WorkPredicate = WorkPredicates.singleDigitalItemMetsWork or WorkPredicates.sierraWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {

      // The calm Work predicate ensures this is safe
      val calmItem = target.data.items.head

      val metsSources = sources.filter(WorkPredicates.singleDigitalItemMetsWork)
      val metsDigitalLocations =
        metsSources.flatMap(_.data.items.flatMap(_.locations))

      val sierraSources = sources.filter(WorkPredicates.sierraWork)
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
