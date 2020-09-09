package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  DigitalLocationDeprecated,
  IdentifierType,
  PhysicalLocationDeprecated,
  SourceIdentifier,
  TransformedBaseWork,
  WorkType
}

object WorkPredicates {
  type WorkPredicate = TransformedBaseWork => Boolean

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

  val sierraPicture: WorkPredicate =
    satisfiesAll(sierraWork, workType(WorkType.Pictures))

  val sierraPictureDigitalImageOr3DObject: WorkPredicate =
    satisfiesAll(
      sierraWork,
      workType(WorkType.DigitalImages) _ or
        workType(WorkType.`3DObjects`) or
        workType(WorkType.Pictures)
    )

  val historicalLibraryMiro: WorkPredicate = singleDigitalItemMiroWork and
    sourceIdentifierSatisfies(_.matches("^[LM].+"))

  def not(pred: WorkPredicate): WorkPredicate = !pred(_)

  def sierraWorkWithId(id: SourceIdentifier)(
    work: TransformedBaseWork): Boolean =
    work.sourceIdentifier == id

  private def physicalLocationExists(work: TransformedBaseWork): Boolean =
    work.data.items.exists { item =>
      item.locations.exists {
        case _: PhysicalLocationDeprecated => true
        case _                             => false
      }
    }

  private def allDigitalLocations(work: TransformedBaseWork): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: DigitalLocationDeprecated => true
        case _                            => false
      }
    }

  private def allPhysicalLocations(work: TransformedBaseWork): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: PhysicalLocationDeprecated => true
        case _                             => false
      }
    }

  private def singleLocation(work: TransformedBaseWork): Boolean =
    work.data.items.forall { item =>
      item.locations.size == 1
    }

  private def sourceIdentifierSatisfies(pred: String => Boolean)(
    work: TransformedBaseWork): Boolean = pred(work.sourceIdentifier.value)

  private def identifierTypeId(id: String)(work: TransformedBaseWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType(id)

  private def workType(workType: WorkType)(work: TransformedBaseWork): Boolean =
    work.data.workType.contains(workType)

  private def satisfiesAll(predicates: (TransformedBaseWork => Boolean)*)(
    work: TransformedBaseWork): Boolean = predicates.forall(_(work))

  implicit class WorkPredicateOps(val predA: WorkPredicate) {
    def or(predB: WorkPredicate): WorkPredicate =
      work => predA(work) || predB(work)

    def and(predB: WorkPredicate): WorkPredicate =
      work => predA(work) && predB(work)
  }
}
