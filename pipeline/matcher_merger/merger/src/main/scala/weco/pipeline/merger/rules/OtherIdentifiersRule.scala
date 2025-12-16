package weco.pipeline.merger.rules

import cats.data.NonEmptyList
import cats.implicits._
import weco.pipeline.merger.models.Sources.findFirstLinkedDigitisedSierraWorkFor
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.identifiers.SourceIdentifier
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.FieldMergeResult

/** Identifiers are merged as follows:
  *
  *   - All source identifiers are merged into Calm works
  *   - Miro identifiers are merged into single or zero item Sierra works
  *   - Sierra works with linked digitised Sierra works have the first of these
  *     linked IDs merged into them
  *   - METS identifiers are not merged as they are not useful
  */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  type FieldData = List[SourceIdentifier]

  // This is a set of the identifierType ids that we allow to be merged
  // from a source work's otherIdentifiers into a target work.
  //
  // - wellcome-digcode is present to persist digcode identifiers from
  //   Encore records onto Calm target works if they are merged, because
  //   digcode identifiers are used as a tagging/classification system.
  private val otherIdentifiersTypeAllowList =
    Set(
      IdentifierType.WellcomeDigcode,
      IdentifierType.SierraIdentifier,
      IdentifierType.CalmRefNo,
      IdentifierType.CalmAltRefNo
    )

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] = {
    val ids = (
      mergeDigitalIntoPhysicalSierraTarget(target, sources) |+|
        mergeIntoTeiTarget(target, sources)
          .orElse(mergeIntoCalmTarget(target, sources))
          .orElse(
            mergeSingleMiroIntoSingleOrZeroItemSierraTarget(target, sources)
          )
          .orElse(
            mergeMiroIntoDigmiroTarget(target, sources)
          )
    ).getOrElse(target.data.otherIdentifiers).distinct

    val mergedSources = (
      List(
        mergeIntoEbscoTarget,
        mergeIntoTeiTarget,
        mergeIntoCalmTarget,
        mergeSingleMiroIntoSingleOrZeroItemSierraTarget,
        mergeMiroIntoDigmiroTarget
      ).flatMap {
        rule =>
          rule.mergedSources(target, sources)
      } ++ findFirstLinkedDigitisedSierraWorkFor(target, sources)
    ).distinct
    FieldMergeResult(
      data = ids,
      sources = mergedSources
    )
  }

  private def getAllowedIdentifiersFromSource(
    source: Work[Identified]
  ): List[SourceIdentifier] =
    (source.sourceIdentifier +: source.data.otherIdentifiers.filter {
      id =>
        otherIdentifiersTypeAllowList.exists(_ == id.identifierType)
    }).distinct

  private trait OtherIdentifiersMergeRule extends PartialRule {
    override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      target.data.otherIdentifiers ++ sources.toList.flatMap(
        getAllowedIdentifiersFromSource
      )
  }

  private val mergeSingleMiroIntoSingleOrZeroItemSierraTarget =
    new OtherIdentifiersMergeRule {
      val isDefinedForTarget: WorkPredicate =
        (singleItemSierra or zeroItemSierra) and sierraPictureDigitalImageOr3DObject
      val isDefinedForSource: WorkPredicate =
        singleDigitalItemMiroWork
      override val isDefinedForSourceList: Seq[Work[Identified]] => Boolean =
        _.count(singleDigitalItemMiroWork) == 1
    }

  private val mergeIntoEbscoTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = ebscoWork
    val isDefinedForSource: WorkPredicate = sierraWork
  }

  private val mergeIntoTeiTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = teiWork
    val isDefinedForSource: WorkPredicate =
      sierraWork or singleDigitalItemMiroWork or singlePhysicalItemCalmWork
  }

  private val mergeIntoCalmTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = singlePhysicalItemCalmWork
    val isDefinedForSource: WorkPredicate =
      singleDigitalItemMetsWork or sierraWork or singleDigitalItemMiroWork
  }

  private val mergeMiroIntoDigmiroTarget = new OtherIdentifiersMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraDigitisedMiro
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  private val mergeDigitalIntoPhysicalSierraTarget = new PartialRule {

    // We don't merge physical/digitised audiovisual works, because the
    // original bib records often contain different data.
    //
    // See the comment on Sources.findFirstLinkedDigitisedSierraWorkFor
    val isDefinedForTarget: WorkPredicate =
      physicalSierra and not(isAudiovisual)

    val isDefinedForSource: WorkPredicate = sierraWork

    def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData = {
      val sourceIdentifiers =
        findFirstLinkedDigitisedSierraWorkFor(target, sources.toList) match {
          case Some(digitisedWork) =>
            target.data.otherIdentifiers ++ digitisedWork.identifiers
          case None =>
            warn(
              s"Unable to find other digitised Sierra identifiers for target work ${target.id}"
            )
            List()
        }

      target.data.otherIdentifiers ++ sourceIdentifiers
    }
  }
}
