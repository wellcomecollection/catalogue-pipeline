package uk.ac.wellcome.platform.merger.rules

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
import weco.catalogue.internal_model.work.{Format, Item, Work}

object WorkPredicates {
  type WorkPredicate = Work[Identified] => Boolean

  private val sierraIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.SierraSystemNumber)
  private val metsIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.METS)
  private val calmIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.CalmRecordIdentifier)
  private val miroIdentified: WorkPredicate = identifierTypeId(
    IdentifierType.MiroImageNumber)

  val sierraWork: WorkPredicate = sierraIdentified
  val zeroItem: WorkPredicate = work => work.data.items.isEmpty
  val singleItem: WorkPredicate = work => work.data.items.size == 1
  val multiItem: WorkPredicate = work => work.data.items.size > 1

  val zeroIdentifiedItems: WorkPredicate =
    work =>
      work.data.items.collect {
        case it @ Item(IdState.Identified(_, _, _), _, _) => it
      }.isEmpty

  /**
    * This is the shape in which we expect the works from the transformers.
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

  // In future this may be changed to `digmiro` for all works
  // where we know that the Miro and METS images are identical
  val sierraDigaids: WorkPredicate =
    satisfiesAll(sierraWork, hasDigcode("digaids"))

  val sierraElectronicVideo: WorkPredicate =
    satisfiesAll(
      sierraWork,
      format(Format.Videos),
      // We may get unidentified items on Sierra bibs, drawn from
      // resources in field 856 -- we don't care about those here.
      zeroIdentifiedItems
    )

  def not(pred: WorkPredicate): WorkPredicate = !pred(_)

  def sierraWorkWithId(id: SourceIdentifier)(work: Work[Identified]): Boolean =
    work.sourceIdentifier == id

  private def physicalLocationExists(work: Work[Identified]): Boolean =
    work.data.items.exists { item =>
      item.locations.exists {
        case _: PhysicalLocation => true
        case _                   => false
      }
    }

  private def allDigitalLocations(work: Work[Identified]): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: DigitalLocation => true
        case _                  => false
      }
    }

  private def allPhysicalLocations(work: Work[Identified]): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: PhysicalLocation => true
        case _                   => false
      }
    }

  private def singleLocation(work: Work[Identified]): Boolean =
    work.data.items.forall { item =>
      item.locations.size == 1
    }

  private def hasDigcode(digcode: String)(work: Work[Identified]): Boolean =
    work.data.otherIdentifiers
      .find(_.identifierType == IdentifierType.WellcomeDigcode)
      .exists(_.value == digcode)

  private def identifierTypeId(id: IdentifierType)(
    work: Work[Identified]): Boolean =
    work.sourceIdentifier.identifierType == id

  private def format(format: Format)(work: Work[Identified]): Boolean =
    work.data.format.contains(format)

  def isAudiovisual(work: Work[Identified]): Boolean =
    work.data.format match {
      case Some(f) if f.isInstanceOf[Format.Audiovisual] => true
      case _                                             => false
    }

  private def satisfiesAll(predicates: (Work[Identified] => Boolean)*)(
    work: Work[Identified]): Boolean = predicates.forall(_(work))

  implicit class WorkPredicateOps(val predA: WorkPredicate) {
    def or(predB: WorkPredicate): WorkPredicate =
      work => predA(work) || predB(work)

    def and(predB: WorkPredicate): WorkPredicate =
      work => predA(work) && predB(work)
  }
}
