package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}

import cats.data.NonEmptyList

/*
 * Items are merged as follows:
 *
 * - The items from digital Sierra works and METS works are added to the items of multi-item
 *   physical Sierra works, or their locations are added to those of single-item
 *   physical Sierra works.
 * - Miro locations are added to single-item Sierra locations.
 */
object ItemsRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[Item[Unminted]]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {
    val rules =
      List(mergeCalmItems, mergeMetsItems, mergeMiroItems, mergeSierraItems)

    val items = rules.flatMap(_(target, sources))
    val mergedSources = sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    }

    FieldMergeResult(
      data = items,
      sources = mergedSources
    )
  }

  private val mergeCalmItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.calmWork

    /**
      * If there is 1 Sierra item, we add the location from the Calm record
      * else we just append the Calm item as we wouldn't know which item to
      * augment.
      */
    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {

      // The Calm transformer always creates a single item with a physical
      // location so this is safe
      val calmItem = sources.head.data.items.head
      val calmLocation = calmItem.locations.head

      target.data.items match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = calmLocation :: sierraItem.locations.collect {
                case location: DigitalLocation => location
              }
            )
          )
        case multipleItems => calmItem :: multipleItems
      }
    }
  }

  /**
    * If there is 1 Sierra item, we add the location from the METS record
    * else we just append METS items as we wouldn't know which item to
    * augment.
    */
  private val mergeMetsItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.singleItemDigitalMets

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {
      val sierraItems = target.data.items
      val metsItems = sources.toList.flatMap(_.data.items)

      debug(s"Merging METS items from ${describeWorks(sources)}")
      sierraItems match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations ++ metsItems.flatMap(_.locations)
            )
          )
        case multipleItems =>
          multipleItems ++ metsItems
      }
    }
  }

  /** This is when we've found a digitised sierra work of a sierra physical work
    * uk.ac.wellcome.platform.transformer.sierra.transformers.SierraMergeCandidates.get776mergeCandidates

    * We actually do nothing, but this is to make sure we make sure
    * a mergedSource is created.
    */
  private val mergeSierraItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.physicalSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.sierraWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      target.data.items
  }

  /** We merge the Miro location to the Sierra work with a single item.
    * If there are multiple items, we don't merge.
    */
  private val mergeMiroItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.miroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {
      val sierraItems = target.data.items
      val miroItems = sources.toList.flatMap(_.data.items)

      debug(s"Merging Miro items from ${describeWorks(sources)}")
      sierraItems match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations ++ miroItems.flatMap(_.locations)
            )
          )
        // This case is unreachable due to the `singleItemSierra` predicate
        // but hard to enforce in the type system
        case _ => _
      }
    }
  }
}
