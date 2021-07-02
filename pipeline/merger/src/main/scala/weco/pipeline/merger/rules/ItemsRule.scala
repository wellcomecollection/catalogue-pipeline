package weco.pipeline.merger.rules

import weco.pipeline.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import cats.data.NonEmptyList
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.LocationType
import weco.catalogue.internal_model.work.{Item, Work}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.FieldMergeResult

/**
  * Items are merged as follows
  *
  * * Sierra - Single items or zero items
  *   * METS works
  *   * Single Miro works, only if the Sierra work has format Picture/Digital Image/3D Object
  * * Sierra - Multi item
  *   * METS works
  */
object ItemsRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  type FieldData = List[Item[IdState.Minted]]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] = {
    val items =
      mergeIntoCalmTarget(target, sources)
        .orElse(mergeMetsIntoSierraTarget(target, sources))
        .orElse(
          mergeSingleMiroIntoSingleOrZeroItemSierraTarget(target, sources)
        )
        .orElse(mergeDigitalIntoPhysicalSierraTarget(target, sources))
        .getOrElse(target.data.items)

    val mergedSources = (
      List(
        mergeIntoCalmTarget,
        mergeMetsIntoSierraTarget,
        mergeSingleMiroIntoSingleOrZeroItemSierraTarget,
        mergeDigitalIntoPhysicalSierraTarget
      ).flatMap { rule =>
        rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)
        ++ knownDuplicateSources(target, sources)
    ).distinct

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
    val isDefinedForTarget: WorkPredicate = sierraWork
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
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

  /** When there is only 1 Sierra item and 1 Miro item, we assume that the
    * Miro work item is associated with the Sierra item and merge the
    * locations onto the Sierra item.
    *
    * When there are no Sierra items, we add the Miro item.
    *
    * This is restricted to the case that the Sierra work is a Picture/Digital Image/3D Object
    *
    * When there are multiple Sierra items, we assume the linked Miro work
    * is definitely associated with * one of them, but unsure of which.
    * Thus we don't append it to the Sierra items to avoid certain duplication,
    * and leave the works unmerged.
    */
  private val mergeSingleMiroIntoSingleOrZeroItemSierraTarget =
    new PartialRule {
      val isDefinedForTarget
        : WorkPredicate = (singleItemSierra or zeroItemSierra) and sierraPictureDigitalImageOr3DObject
      val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
      override val isDefinedForSourceList: Seq[Work[Identified]] => Boolean =
        _.count(singleDigitalItemMiroWork) == 1

      def rule(
        target: Work.Visible[Identified],
        sources: NonEmptyList[Work[Identified]]
      ): FieldData =
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

  // While we can't merge a Miro item into a Sierra work with multiple items (as described above),
  // we know that in some cases the duplication is such that we can treat the Miro source as having
  // been entirely subsumed into the target despite not having used any of its data.
  //
  // At the moment, the only case of this is when we have a digaids work: we know that
  // the Miro item is identical to the METS item.
  def knownDuplicateSources(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): Seq[Work[Identified]] =
    if (sierraDigaids(target) && sources.exists(singleDigitalItemMetsWork)) {
      sources.filter(singleDigitalItemMiroWork)
    } else {
      Nil
    }

  /** When records are harvested from Calm, both a bib and an item record are
    * created in Sierra.  How we combine these is slightly complicated:
    *
    *     - We can’t just take the Calm item – we would lose any access information,
    *       which is only populated in Sierra.
    *     - We can’t just take the Sierra item – when the Calm/Sierra harvest happens,
    *       it smushes all the access data into 506 $a, undoing all the work we’ve done
    *       to tidy up the terms in the Calm transformer, e.g. de-duplicated "closed until".
    *
    * For now, we choose to just take the Sierra item (or rather, take any items except
    * the Calm item).  We lose some of the Calm transformer niceties, but they're not worth
    * the additional complexity it would add here.
    *
    * Additionally, it's possible the CALM fields will be split into separate 506 subfields
    * in a future version of the Calm/Sierra harvest, which would allow us to mirror the tidy-up
    * logic in the Sierra transformer.
    * For now, we keep all the items *except* the Calm item.  This means
    * we'll also pick up any items linked to the Sierra work from METS or Miro.
    *
    */
  private val mergeIntoCalmTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = singlePhysicalItemCalmWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or
         singleDigitalItemMiroWork or
         sierraWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      sources.map(_.data.items).toList.flatten
  }

  /** If we're merging a physical/digitised Sierra work pair, make
    * sure we copy any items from the digitised bib that are stored
    * in the 856 web link field.
    *
    */
  private val mergeDigitalIntoPhysicalSierraTarget = new PartialRule {

    // We don't merge physical/digitised audiovisual works, because the
    // original bib records often contain different data.
    //
    // See the comment on Sources.findFirstLinkedDigitisedSierraWorkFor
    val isDefinedForTarget: WorkPredicate = physicalSierra and not(
      isAudiovisual
    )

    val isDefinedForSource: WorkPredicate = sierraWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      findFirstLinkedDigitisedSierraWorkFor(target, sources.toList)
        .map { digitisedWork =>
          val onlineItems = digitisedWork.data.items
            .filter { it =>
              it.locations.exists(_.locationType == LocationType.OnlineResource)
            }

          target.data.items ++ onlineItems
        }
        .getOrElse(Nil)
  }
}
