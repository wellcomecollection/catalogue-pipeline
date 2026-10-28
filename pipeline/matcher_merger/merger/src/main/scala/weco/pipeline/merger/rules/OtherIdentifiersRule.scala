package weco.pipeline.merger.rules

import cats.data.NonEmptyList
import cats.implicits._
import weco.pipeline.merger.models.Sources.findFirstLinkedDigitisedLmsWorkFor
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.identifiers.SourceIdentifier
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.FieldMergeResult

/** Identifiers are merged as follows:
  *
  *   - All source identifiers are merged into Calm/Axiell works
  *   - Miro identifiers are merged into single or zero item LMS works
  *   - LMS works with linked digitised LMS works have the first of these linked
  *     IDs merged into them
  *   - METS identifiers are not merged as they are not useful
  */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  type FieldData = List[SourceIdentifier]

  // This is a set of the identifierType ids that we allow to be merged
  // from a source work's otherIdentifiers into a target work.
  //
  // - wellcome-digcode is present to persist digcode identifiers from
  //   Encore records onto Calm/Axiell target works if they are merged, because
  //   digcode identifiers are used as a tagging/classification system.
  private val otherIdentifiersTypeAllowList: Set[IdentifierType] =
    Set(
      IdentifierType.WellcomeDigcode,
      IdentifierType.SierraIdentifier,
      IdentifierType.CalmRefNo,
      IdentifierType.CalmAltRefNo
    )

  // A FOLIO work is identified by its instance UUID, so its HRID and Sierra
  // b-number are other identifiers and have to be allowed explicitly.
  private val folioIdentifiersTypeAllowList: Set[IdentifierType] =
    Set(
      IdentifierType.FolioInstanceHrid,
      IdentifierType.SierraSystemNumber
    )

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] = {
    val ids = (
      mergeDigitalIntoPhysicalLmsWorkTarget(target, sources) |+|
        mergeIntoTeiTarget(target, sources)
          .orElse(mergeIntoCalmOrAxiellTarget(target, sources))
          .orElse(
            mergeSingleMiroIntoSingleOrZeroItemLmsWorkTarget(target, sources)
          )
          .orElse(
            mergeMiroIntoDigmiroTarget(target, sources)
          )
    ).getOrElse(target.data.otherIdentifiers).distinct

    val mergedSources = (
      List(
        mergeIntoEbscoTarget,
        mergeIntoTeiTarget,
        mergeIntoCalmOrAxiellTarget,
        mergeSingleMiroIntoSingleOrZeroItemLmsWorkTarget,
        mergeMiroIntoDigmiroTarget
      ).flatMap {
        rule =>
          rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedLmsWorkFor(target, sources)
    ).distinct
    FieldMergeResult(
      data = ids,
      sources = mergedSources
    )
  }

  private def getAllowedIdentifiersFromSource(
    source: Work[Identified]
  ): List[SourceIdentifier] = {
    val allowList =
      if (
        source.sourceIdentifier.identifierType == IdentifierType.FolioInstance
      )
        otherIdentifiersTypeAllowList ++ folioIdentifiersTypeAllowList
      else otherIdentifiersTypeAllowList

    (source.sourceIdentifier +: source.data.otherIdentifiers.filter {
      id =>
        allowList.contains(id.identifierType)
    }).distinct
  }

  private trait OtherIdentifiersMergeRule extends PartialRule {
    override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      target.data.otherIdentifiers ++ sources.toList.flatMap(
        getAllowedIdentifiersFromSource
      )
  }

  private val mergeSingleMiroIntoSingleOrZeroItemLmsWorkTarget =
    new OtherIdentifiersMergeRule {
      val isDefinedForTarget: WorkPredicate =
        (singleItemLmsWork or zeroItemLmsWork) and lmsWorkPictureDigitalImageOr3DObject
      val isDefinedForSource: WorkPredicate =
        singleDigitalItemMiroWork
      override val isDefinedForSourceList: Seq[Work[Identified]] => Boolean =
        _.count(singleDigitalItemMiroWork) == 1
    }

  private val mergeIntoEbscoTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = ebscoWork
    val isDefinedForSource: WorkPredicate = lmsWork
  }

  private val mergeIntoTeiTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = teiWork
    val isDefinedForSource: WorkPredicate =
      lmsWork or singleDigitalItemMiroWork or singlePhysicalItemCalmWork or singlePhysicalItemAxiellWork
  }

  private val mergeIntoCalmOrAxiellTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate =
      singlePhysicalItemCalmWork or singlePhysicalItemAxiellWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or lmsWork or singleDigitalItemMiroWork
  }

  private val mergeMiroIntoDigmiroTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = lmsWorkDigitisedMiro
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  private val mergeDigitalIntoPhysicalLmsWorkTarget = new PartialRule {

    // We don't merge physical/digitised audiovisual works, because the
    // original bib records often contain different data.
    //
    // See the comment on Sources.findFirstLinkedDigitisedLmsWorkFor
    val isDefinedForTarget: WorkPredicate =
      physicalLmsWork and not(isAudiovisual)

    val isDefinedForSource: WorkPredicate = lmsWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData = {
      val sourceIdentifiers =
        findFirstLinkedDigitisedLmsWorkFor(target, sources.toList) match {
          case Some(digitisedWork) =>
            target.data.otherIdentifiers ++ digitisedWork.identifiers
          case None =>
            warn(
              s"Unable to find other digitised LMS identifiers for target work ${target.id}"
            )
            List()
        }

      target.data.otherIdentifiers ++ sourceIdentifiers
    }
  }
}
