package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate
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
      List(mergeCalmItems, mergeMetsItems, mergeMiroItems)

    val items = mergeCalmItems(target, sources)
      .orElse(mergeMetsItems(target, sources))
      .orElse(mergeMiroItems(target, sources))
      .getOrElse(target.data.items)

    val mergedSources = sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    } ++ getDigitisedCopiesOfSierraWork(target, sources)

    FieldMergeResult(
      data = items,
      sources = mergedSources
    )
  }

  /** This is when we've found a digitised sierra work of a sierra physical work
    * uk.ac.wellcome.platform.transformer.sierra.transformers.SierraMergeCandidates.get776mergeCandidates
    *
    * We get all the digitised SourceIdentifiers from the merge candidates
    * and search through the sources for them.
    */
  private def getDigitisedCopiesOfSierraWork(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): Seq[TransformedBaseWork] = {
    target.data.mergeCandidates
      .filter(_.reason.contains("Physical/digitised Sierra work"))
      .map(_.identifier)
      .flatMap(sourceIdentifier =>
        sources.filter(source => source.sourceIdentifier == sourceIdentifier))
  }

  /**
    * If there is 1 Sierra item, we replace the `PhysicalLocation` with
    * the one from the Calm record.
    *
    * Otherwise we add the item to the items list.
    *
    * This logic is going to be removed soon as we make the Calm
    * record the `target`.
    */
  private val mergeCalmItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.calmWork

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
              // We remove any `PhysicalLocation`s and add the calmLocation
              // as the only `PhysicalLocation`
              locations = calmLocation :: sierraItem.locations.filter(_ match {
                case _: PhysicalLocation => false
                case _                   => true
              })
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

  /** We merge the Miro location to the Sierra work with a single item.
    * If there are multiple items, we don't merge as we wouldn't know
    * which item to attache the location to.
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
        case items => items
      }
    }
  }
}
