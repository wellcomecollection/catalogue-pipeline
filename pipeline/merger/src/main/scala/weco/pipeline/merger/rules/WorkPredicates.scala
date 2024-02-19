package weco.pipeline.merger.rules

import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.{Format, Work}

import scala.Function.const

object WorkPredicates {
  type WorkPredicate = Work[Identified] => Boolean

  private val sierraIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.SierraSystemNumber
  )
  private val metsIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.METS
  )
  private val calmIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.CalmRecordIdentifier
  )
  private val teiIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.Tei
  )
  private val isVisible: WorkPredicate = work =>
    work.isInstanceOf[Work.Visible[_]]
  private val miroIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.MiroImageNumber
  )

  val sierraWork: WorkPredicate = sierraIdentified
  val zeroItem: WorkPredicate = work => work.data.items.isEmpty
  val singleItem: WorkPredicate = work => work.data.items.size == 1
  val multiItem: WorkPredicate = work => work.data.items.size > 1

  val anyWork: WorkPredicate = const(true)

  val zeroIdentifiedItems: WorkPredicate =
    work => !work.data.items.exists { _.id.isInstanceOf[IdState.Identified] }

  val teiWork: WorkPredicate = satisfiesAll(
    teiIdentified,
    zeroItem,
    isVisible
  )

  /** This is the shape in which we expect the works from the transformers.
    * We're specific here as the merging rules often rely on the shape of the
    * transformers outputs.
    */
  val singlePhysicalItemCalmWork: WorkPredicate = satisfiesAll(
    calmIdentified,
    singleItem,
    singleLocation,
    allPhysicalLocations
  )

  val singleDigitalItemMetsWork: WorkPredicate = satisfiesAll(
    metsIdentified,
    singleItem,
    allDigitalLocations
  )

  val singleDigitalItemMiroWork: WorkPredicate = satisfiesAll(
    miroIdentified,
    singleItem,
    allDigitalLocations
  )

  val zeroItemSierra: WorkPredicate = satisfiesAll(sierraWork, zeroItem)
  val singleItemSierra: WorkPredicate = satisfiesAll(sierraWork, singleItem)
  val multiItemSierra: WorkPredicate = satisfiesAll(sierraWork, multiItem)

  val physicalSierra: WorkPredicate =
    satisfiesAll(sierraWork, physicalLocationExists)

  val sierraPictureOrEphemera: WorkPredicate =
    satisfiesAll(
      sierraWork,
      format(Format.Pictures) _ or format(Format.Ephemera)
    )

  val sierraPictureDigitalImageOr3DObject: WorkPredicate =
    satisfiesAll(
      sierraWork,
      format(Format.DigitalImages) _ or
        format(Format.`3DObjects`) or
        format(Format.Pictures)
    )

  val digaids: WorkPredicate = hasDigcode("digaids")
  val digmiro: WorkPredicate = hasDigcode("digmiro")

  // The AIDS posters (digaids) have all already been re-digitised
  // and marked with `digaids`, whereas going forward Miro works
  // that have been re-digitised will be marked as `digmiro`.
  val sierraDigitisedMiro: WorkPredicate =
    satisfiesAll(sierraWork, digaids or digmiro)

  val sierraDigitisedAv: WorkPredicate =
    satisfiesAll(
      sierraWork,
      isAudiovisual,
      // We may get unidentified items on Sierra bibs, drawn from
      // resources in field 856 -- we don't care about those here.
      zeroIdentifiedItems
    )

  val physicalDigital: WorkPredicate = hasMergeReason(
    "Physical/digitised Sierra work"
  )

  def not(pred: WorkPredicate): WorkPredicate = !pred(_)

  def sierraWorkWithId(id: SourceIdentifier)(work: Work[Identified]): Boolean =
    work.sourceIdentifier == id

  private def physicalLocationExists(work: Work[Identified]): Boolean =
    work.data.items.exists {
      item =>
        item.locations.exists {
          case _: PhysicalLocation => true
          case _                   => false
        }
    }

  def allDigitalLocations(work: Work[Identified]): Boolean =
    work.data.items.forall {
      item =>
        item.locations.forall {
          case _: DigitalLocation => true
          case _                  => false
        }
    }

  private def allPhysicalLocations(work: Work[Identified]): Boolean =
    work.data.items.forall {
      item =>
        item.locations.forall {
          case _: PhysicalLocation => true
          case _                   => false
        }
    }

  private def singleLocation(work: Work[Identified]): Boolean =
    work.data.items.forall {
      item =>
        item.locations.size == 1
    }

  private def hasDigcode(digcode: String)(work: Work[Identified]): Boolean =
    work.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.WellcomeDigcode)
      .exists(_.value == digcode)

  private def hasMergeReason(reason: String)(work: Work[Identified]): Boolean =
    work.state.mergeCandidates.exists(_.reason.contains(reason))
  private def identifierTypeId(
    id: IdentifierType
  )(work: Work[Identified]): Boolean =
    work.sourceIdentifier.identifierType == id

  private def format(format: Format)(work: Work[Identified]): Boolean =
    work.data.format.contains(format)

  def isAudiovisual(work: Work[Identified]): Boolean =
    work.data.format match {
      case Some(f) if f.isInstanceOf[Format.Audiovisual] => true
      case _                                             => false
    }

  def hasPhysicalDigitalMergeCandidate(work: Work[Identified]): Boolean =
    work.state.mergeCandidates
      .exists(_.reason.contains("Physical/digitised Sierra work"))

  private def satisfiesAll(predicates: (Work[Identified] => Boolean)*)(
    work: Work[Identified]
  ): Boolean = predicates.forall(_(work))

  implicit class WorkPredicateOps(val predA: WorkPredicate) {
    def or(predB: WorkPredicate): WorkPredicate =
      work => predA(work) || predB(work)

    def and(predB: WorkPredicate): WorkPredicate =
      work => predA(work) && predB(work)
  }
}
