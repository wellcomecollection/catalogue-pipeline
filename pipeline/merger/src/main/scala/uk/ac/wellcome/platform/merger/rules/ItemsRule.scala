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
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] =
    FieldMergeResult(
      fieldData = mergeItems(target, sources),
      redirects = sources.filter { source =>
        (mergeMetsItems(target, source) orElse mergeMiroPhysicalAndDigitalItems(
          target,
          source)).isDefined
      }
    )

  private def mergeItems(target: UnidentifiedWork,
                         sources: Seq[TransformedBaseWork]): FieldData = {
    // TODO: the merging behaviour here is temporary until jtweed confirms the
    // exact rules
    val mergedTarget = mergeCalmItems(target, sources)
      .map(items => target.withData(_.copy(items = items)))
      .getOrElse(target)
    mergeMetsItems(mergedTarget, sources)
      .orElse(mergeMiroPhysicalAndDigitalItems(mergedTarget, sources))
      .getOrElse(mergedTarget.data.items)
  }

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
              locations = calmLocation :: sierraItem.locations.collect {
                case location: DigitalLocation => location
              }
            )
          )
        case items => calmItem :: items
      }
    }
  }

  private val mergeMetsItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.singleItemDigitalMets

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData = {
      val sierraItems = target.data.items
      val metsItems = sources.toList.flatMap(_.data.items)
      val metsUrls = metsItems.flatMap(_.locations).collect {
        case DigitalLocation(url, _, _, _, _, _) => url
      }
      debug(s"Merging METS items from ${describeWorks(sources)}")
      sierraItems match {
        case List(sierraItem) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations.filterNot(hasUrl(metsUrls)) ++
                metsItems.flatMap(_.locations)
            )
          )
        case _ =>
          sierraItems.filterNot(_.locations.exists(hasUrl(metsUrls))) ++
            metsItems
      }
    }
  }

  private val mergeMiroPhysicalAndDigitalItems = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource
      : WorkPredicate = WorkPredicates.miroWork or WorkPredicates.digitalSierra

    def rule(target: UnidentifiedWork,
             sources: NonEmptyList[TransformedBaseWork]): FieldData =
      (target.data.items, sources.toList.partition(WorkPredicates.miroWork)) match {
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

  private def hasUrl(matchUrls: Seq[String])(location: Location) =
    location match {
      case DigitalLocation(url, _, _, _, _, _) if matchUrls.contains(url) =>
        true
      case _ => false
    }
}
