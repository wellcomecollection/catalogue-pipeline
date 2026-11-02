package weco.pipeline.merger.rules

import weco.pipeline.merger.models.Sources.findFirstLinkedDigitisedLmsWorkFor
import cats.data.NonEmptyList
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Item, Work}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.{FieldMergeResult, MergerItem}

/** Items are merged as follows
  *
  * * LMS - Single items or zero items * METS works * Single Miro works, only if
  * the LMS work has format Picture/Digital Image/3D Object * LMS - Multi item *
  * METS works
  */
object ItemsRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  import scala.language.implicitConversions
  implicit def _item[T](item: Item[T]): MergerItem[T] = MergerItem[T](item)

  type FieldData = List[Item[IdState.Minted]]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] = {
    val items =
      mergeIntoTeiTarget(target, sources)
        .orElse(mergeIntoCalmOrAxiellTarget(target, sources))
        .orElse(mergeMetsIntoLmsWorkTarget(target, sources))
        .orElse(
          mergeSingleMiroIntoSingleOrZeroItemLmsWorkTarget(target, sources)
        )
        .orElse(mergeDigitalIntoPhysicalLmsWorkTarget(target, sources))
        .getOrElse(unmergedItems(target))

    val mergedSources = (
      List(
        mergeIntoTeiTarget,
        mergeIntoCalmOrAxiellTarget,
        mergeMetsIntoLmsWorkTarget,
        mergeSingleMiroIntoSingleOrZeroItemLmsWorkTarget
      ).flatMap {
        rule =>
          rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedLmsWorkFor(target, sources)
        ++ knownDuplicateSources(target, sources)
    ).distinct

    FieldMergeResult(
      data = items,
      sources = mergedSources
    )
  }

  /** The target's own items when no source replaces them. */
  private def unmergedItems(target: Work.Visible[Identified]): FieldData =
    if (bornDigitalArchiveStubWork(target)) Nil else target.data.items

  /** When there is only 1 LMS item, we assume that the METS work item is
    * associated with that and merge the locations onto the LMS item.
    *
    * Otherwise (including if there are no LMS items) we append the METS item to
    * the LMS items
    */
  private val mergeMetsIntoLmsWorkTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = lmsWork
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      target.data.items match {
        case List(lmsWorkItem) =>
          List(lmsWorkItem.appendLocationsFrom(sources))
        case _ => target.data.items ++ sources.toList.flatMap(_.data.items)
      }
  }

  /** When there is only 1 LMS item and 1 Miro item, we assume that the Miro
    * work item is associated with the LMS item and merge the locations onto the
    * LMS item.
    *
    * When there are no LMS items, we add the Miro item.
    *
    * This is restricted to the case that the LMS work is a Picture/Digital
    * Image/3D Object
    *
    * When there are multiple LMS items, we assume the linked Miro work is
    * definitely associated with * one of them, but unsure of which. Thus we
    * don't append it to the LMS items to avoid certain duplication, and leave
    * the works unmerged.
    */
  private val mergeSingleMiroIntoSingleOrZeroItemLmsWorkTarget =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate =
        (singleItemLmsWork or zeroItemLmsWork) and lmsWorkPictureDigitalImageOr3DObject
      val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
      override val isDefinedForSourceList: Seq[Work[Identified]] => Boolean =
        _.count(singleDigitalItemMiroWork) == 1

      def rule(
        target: Work.Visible[Identified],
        sources: NonEmptyList[Work[Identified]]
      ): FieldData =
        target.data.items match {
          case List(lmsWorkItem) =>
            List(lmsWorkItem.appendLocationsFrom(sources))
          case _ => sources.toList.flatMap(_.data.items)
        }
    }

  // While we can't merge a Miro item into an LMS work with multiple items (as described above),
  // we know that in some cases the duplication is such that we can treat the Miro source as having
  // been entirely subsumed into the target despite not having used any of its data.
  //
  // At the moment, the only case of this is when we have a (re)digitised Miro work: we know that
  // the Miro item is identical to the METS item.
  def knownDuplicateSources(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): Seq[Work[Identified]] =
    if (
      lmsWorkDigitisedMiro(target) && sources.exists(singleDigitalItemMetsWork)
    ) {
      sources.filter(singleDigitalItemMiroWork)
    } else {
      Nil
    }

  /** When records are harvested from Calm/Axiell into the LMS, or synced from
    * Axiell to FOLIO, both a bib and an item record are created in the LMS. The
    * LMS item will have more interesting information about access status, hold
    * count, etc. The Calm/Axiell items are just stubs.
    *
    * See CalmItems.scala and axiell_work_builder.py -- the Calm/Axiell item is
    * essentially a single item with a ClosedStores PhysicalLocation (and
    * optional accessConditions), e.g.
    *
    * Item { locations: [ PhysicalLocation { locationType = ClosedStores,
    * accessConditions = [...] } ] }
    *
    * For this reason, we keep all the items *except* the Calm/Axiell item. This
    * means we'll also pick up any items linked to the LMS work from METS or
    * Miro.
    */
  private val mergeIntoCalmOrAxiellTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate =
      singlePhysicalItemCalmWork or singlePhysicalItemAxiellWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or
        singleDigitalItemMiroWork or
        lmsWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      sources.map(_.data.items).toList.flatten
  }

  private val mergeIntoTeiTarget = new PartialRule {
    val isDefinedForTarget: WorkPredicate = teiWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or
        singleDigitalItemMiroWork or
        lmsWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      sources.map(_.data.items).toList.flatten
  }

  /** If we're merging a physical/digitised LMS work pair, make sure we copy any
    * items from the digitised bib that are stored in the 856 web link field.
    */
  private val mergeDigitalIntoPhysicalLmsWorkTarget = new PartialRule {

    // We don't merge physical/digitised audiovisual works, because the
    // original bib records often contain different data.
    //
    // See the comment on Sources.findFirstLinkedDigitisedLmsWorkFor
    val isDefinedForTarget: WorkPredicate =
      physicalLmsWork and physicalDigital and not(
        isAudiovisual
      )

    val isDefinedForSource: WorkPredicate = lmsWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      findFirstLinkedDigitisedLmsWorkFor(target, sources.toList)
        .map {
          digitisedWork =>
            val onlineItems =
              digitisedWork.data.items.filter(_.isAvailableOnline)

            target.data.items ++ onlineItems
        }
        .getOrElse(target.data.items)
  }
}
