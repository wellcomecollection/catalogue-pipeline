package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  Item,
  TransformedBaseWork,
  UnidentifiedWork,
  Unminted
}
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}

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
    sources: Seq[TransformedBaseWork]): MergeResult[FieldData] =
    MergeResult(
      fieldData = (mergeMetsItems orElse mergeMiroPhysicalAndDigitalItems
        orElse (identityOnTarget andThen (_.data.items)))((target, sources)),
      redirects = sources.filter { source =>
        (mergeMetsItems orElse mergeMiroPhysicalAndDigitalItems)
          .isDefinedAt((target, List(source)))
      }
    )

  private lazy val mergeMetsItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.singleItemDigitalMets

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): FieldData = {
      val sierraItems = target.data.items
      val metsItems = sources.flatMap(_.data.items)
      info(s"Merging METS items from ${describeWorks(sources)}")
      sierraItems match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations ++ metsItems.flatMap(_.locations)
            )
          )
        case _ => sierraItems ++ metsItems
      }
    }
  }

  private lazy val mergeMiroPhysicalAndDigitalItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource
      : WorkPredicate = WorkPredicates.miroWork or WorkPredicates.digitalSierra

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): FieldData =
      (target.data.items, sources.partition(WorkPredicates.miroWork)) match {
        case (List(sierraSingleItem), (miroSources, sierraSources)) =>
          List(
            sierraSingleItem.copy(
              locations = sierraSingleItem.locations ++
                (sierraSources ++ miroSources).flatMap(
                  _.data.items.flatMap(_.locations)
                )
            )
          )
        case (multipleSierraItems, (_, sierraSources)) =>
          multipleSierraItems ++ sierraSources.flatMap(_.data.items)
      }
  }
}
