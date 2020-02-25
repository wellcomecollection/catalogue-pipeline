package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  Item,
  Location,
  LocationType,
  TransformedBaseWork,
  UnidentifiedWork,
  Unminted
}
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.rules.WorkFilters.WorkFilter

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
      fieldData = composeRules(liftIntoTarget)(
        metsItemsRule,
        miroItemsRule,
        physicalDigitalItemsRule)(target, sources).data.items,
      redirects = sources.filter { source =>
        (metsItemsRule orElse miroItemsRule orElse physicalDigitalItemsRule)
          .isDefinedAt((target, List(source)))
      }
    )

  private def liftIntoTarget(target: UnidentifiedWork)(
    items: FieldData): UnidentifiedWork = target withData { data =>
    data.copy(items = items)
  }

  private lazy val metsItemsRule = new PartialRule {
    val isDefinedForTarget: WorkFilter = WorkFilters.sierraWork
    val isDefinedForSource: WorkFilter = WorkFilters.singleItemDigitalMets

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): FieldData = {
      val sierraItems = target.data.items
      val metsItems = sources.flatMap(_.data.items)
      info(s"Merging METS items from ${describeWorks(sources)}")
      sierraItems match {
        case List(sierraItem) =>
          List(
            metsItems.foldLeft(sierraItem) { (mergedItem, metsItem) =>
              MetsUtils.mergeLocations(mergedItem, metsItem)
            }
          )
        case _ =>
          sierraItems.filterNot { sierraItem =>
            metsItems.exists { metsItem =>
              MetsUtils.shouldIgnoreSierraItem(sierraItem, metsItem)
            }
          } ++ metsItems
      }
    }
  }

  private lazy val miroItemsRule = new PartialRule {
    val isDefinedForTarget: WorkFilter = WorkFilters.singleItemSierra
    val isDefinedForSource: WorkFilter = WorkFilters.singleItemMiro

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): FieldData = {
      info(s"Merging Miro items from ${describeWorks(sources)}")
      (target.data.items, sources.flatMap(_.data.items)) match {
        case (List(sierraItem), miroItems) =>
          List(
            sierraItem.copy(
              locations = sierraItem.locations ++ miroItems.flatMap(_.locations)
            )
          )
        case _ => Nil
      }
    }
  }

  private lazy val physicalDigitalItemsRule = new PartialRule {
    val isDefinedForTarget: WorkFilter = WorkFilters.physicalSierra
    val isDefinedForSource: WorkFilter = WorkFilters.digitalSierra

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): FieldData = {
      info(s"Merging physical and digital items from ${describeWorks(sources)}")
      (target.data.items, sources.flatMap(_.data.items)) match {
        case (List(physicalItem), digitalItems) =>
          List(
            physicalItem.copy(
              locations = physicalItem.locations ++
                digitalItems.flatMap(_.locations)
            )
          )
        case (physicalItems, digitalItems) => physicalItems ++ digitalItems
      }
    }
  }
}

private object MetsUtils {
  def shouldIgnoreSierraItem(sierraItem: Item[Unminted],
                             metsItem: Item[Unminted]): Boolean = {
    (sierraItem.locations, getLocation(metsItem)) match {
      case (List(sierraLocation), Some(metsLocation)) =>
        shouldIgnoreLocation(sierraLocation, metsLocation.url)
      case _ => false
    }
  }

  def shouldIgnoreLocation(location: Location, metsUrl: String): Boolean =
    location match {
      case DigitalLocation(_, LocationType("iiif-image", _, _), _, _, _, _) =>
        true
      case DigitalLocation(url, _, _, _, _, _) if url.equals(metsUrl) => true
      case _                                                          => false
    }

  def mergeLocations(sierraItem: Item[Unminted],
                     metsItem: Item[Unminted]): Item[Unminted] =
    getLocation(metsItem)
      .map { metsLocation =>
        sierraItem.copy(
          locations =
            sierraItem.locations.filterNot(
              shouldIgnoreLocation(_, metsLocation.url))
              :+ metsLocation
        )
      }
      .getOrElse(sierraItem)

  private def getLocation(metsItem: Item[Unminted]): Option[DigitalLocation] =
    metsItem.locations.collectFirst {
      case location: DigitalLocation => location
    }
}
