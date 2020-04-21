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
      List(mergeCalmItems, mergeMetsItems, mergeMiroItems)

    val items =
      mergeIntoCalmTarget(target, sources)
        .orElse(mergeCalmItems(target, sources))
        .orElse(mergeMetsItems(target, sources))
        .orElse(mergeMiroItems(target, sources))
        .getOrElse(target.data.items)

    val calmMergedSources =
      sources.filter(mergeIntoCalmTarget(target, _).isDefined)

    val mergedSources = sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    } ++ getDigitisedCopiesOfSierraWork(target, sources) ++ calmMergedSources

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

  /** We merge the Miro location to the Sierra work with a single item
    * as we assume that this is the only item that the Miro image
    * could be associated with.
    *
    * If we have multiple items, we assume it is definitely associated with
    * one of the Sierra items, but unsure of which. We thus don't append it
    * to the Sierra items to avoid certain duplication, and leave the works
    * unmerged.
    */
  private val mergeMiroItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.miroWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {
      // This is safe due to the `singleItemSierra` predicate
      val sierraItem = target.data.items.head
      val miroItems = sources.toList.flatMap(_.data.items)

      debug(s"Merging Miro items from ${describeWorks(sources)}")

      List(
        sierraItem.copy(
          locations = sierraItem.locations ++ miroItems.flatMap(_.locations)
        ))
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
    val isDefinedForTarget: WorkPredicate = WorkPredicates.calmWork
    val isDefinedForSource
      : WorkPredicate = WorkPredicates.metsWork or WorkPredicates.sierraWork

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {

      // The calmWork predicate ensures this is safe
      val calmItem = target.data.items.head

      val metsSources = sources.filter(WorkPredicates.metsWork)
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
