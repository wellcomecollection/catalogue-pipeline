package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal._
import WorkState.Identified
import uk.ac.wellcome.models.work.internal.Format.Audiovisual

object WorkPredicates {
  type WorkPredicate = Work[Identified] => Boolean

  private val sierraIdentified: WorkPredicate = identifierTypeId(
    "sierra-system-number")
  private val metsIdentified: WorkPredicate = identifierTypeId("mets")
  private val calmIdentified: WorkPredicate = identifierTypeId("calm-record-id")
  private val miroIdentified: WorkPredicate = identifierTypeId(
    "miro-image-number")

  val sierraWork: WorkPredicate = sierraIdentified
  val zeroItem: WorkPredicate = work => work.data.items.isEmpty
  val singleItem: WorkPredicate = work => work.data.items.size == 1
  val multiItem: WorkPredicate = work => work.data.items.size > 1

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

  def not(pred: WorkPredicate): WorkPredicate = !pred(_)

  def sierraWorkWithId(id: SourceIdentifier)(work: Work[Identified]): Boolean =
    work.sourceIdentifier == id

  private def physicalLocationExists(work: Work[Identified]): Boolean =
    work.data.items.exists { item =>
      item.locations.exists {
        case _: PhysicalLocationDeprecated => true
        case _                             => false
      }
    }

  private def allDigitalLocations(work: Work[Identified]): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: DigitalLocationDeprecated => true
        case _                            => false
      }
    }

  private def allPhysicalLocations(work: Work[Identified]): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: PhysicalLocationDeprecated => true
        case _                             => false
      }
    }

  private def singleLocation(work: Work[Identified]): Boolean =
    work.data.items.forall { item =>
      item.locations.size == 1
    }

  private def hasDigcode(digcode: String)(work: Work[Identified]): Boolean =
    work.data.otherIdentifiers
      .find(_.identifierType.id == "wellcome-digcode")
      .exists(_.value == digcode)

  private def identifierTypeId(id: String)(work: Work[Identified]): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType(id)

  private def format(format: Format)(work: Work[Identified]): Boolean =
    work.data.format.contains(format)

  def isAudiovisual(work: Work[Identified]): Boolean =
    work.data.format match {
      case Some(f) if f.isInstanceOf[Audiovisual] => true
      case _ => false
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
