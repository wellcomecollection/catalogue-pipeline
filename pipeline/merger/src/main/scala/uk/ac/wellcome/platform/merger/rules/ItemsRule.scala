package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}
import cats.data.NonEmptyList

/**
  * Items are merged as follows
  *
  * * Sierra - Single items
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
        mergeMetsIntoSingleItemSierraTarget,
        mergeMiroIntoSingleItemSierraTarget,
        mergeIntoMultiItemSierraTarget)

    val items =
      mergeIntoCalmTarget(target, sources)
        .orElse(mergeMetsIntoSingleItemSierraTarget(target, sources).orElse(
          mergeMiroIntoSingleItemSierraTarget(target, sources)))
        .orElse(mergeIntoMultiItemSierraTarget(target, sources))
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

  /** When there is only 1 Sierra item, we assume that the METS work item
    * is associated with that and merge the locations onto the Sierra item.
    */
  private val mergeMetsIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.metsWork

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

  /** When there is only 1 Sierra item, we assume that the Miro work item
    * is associated with that and merge the locations onto the Sierra item.
    */
  private val mergeMiroIntoSingleItemSierraTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.miroWork

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
    val isDefinedForTarget: WorkPredicate = WorkPredicates.multiItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.metsWork

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
