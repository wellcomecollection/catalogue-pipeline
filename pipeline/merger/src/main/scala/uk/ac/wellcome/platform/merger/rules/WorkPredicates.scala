package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  IdentifierType,
  PhysicalLocation,
  TransformedBaseWork,
  WorkType
}

object WorkPredicates {
  type WorkPredicate = TransformedBaseWork => Boolean

  val sierraWork: WorkPredicate = identifierTypeId("sierra-system-number")
  val metsWork: WorkPredicate = identifierTypeId("mets")
  val miroWork: WorkPredicate = identifierTypeId("miro-image-number")
  val calmWork: WorkPredicate = identifierTypeId("calm-record-id")

  val singleItem: WorkPredicate = work => work.data.items.size == 1

  val singleItemDigitalMets: WorkPredicate = satisfiesAll(
    metsWork,
    singleItem,
    allDigitalLocations
  )

  val singleItemMiro: WorkPredicate = satisfiesAll(miroWork, singleItem)

  val singleItemSierra: WorkPredicate = satisfiesAll(sierraWork, singleItem)

  val physicalSierra: WorkPredicate =
    satisfiesAll(sierraWork, physicalLocationExists)

  val digitalSierra: WorkPredicate =
    satisfiesAll(sierraWork, singleItem, allDigitalLocations)

  val sierraPicture: WorkPredicate =
    satisfiesAll(sierraWork, workType(WorkType.Pictures))

  def not(pred: WorkPredicate): WorkPredicate = !pred(_)

  private def physicalLocationExists(work: TransformedBaseWork): Boolean =
    work.data.items.exists { item =>
      item.locations.exists {
        case _: PhysicalLocation => true
        case _                   => false
      }
    }

  private def allDigitalLocations(work: TransformedBaseWork): Boolean =
    work.data.items.forall { item =>
      item.locations.forall {
        case _: DigitalLocation => true
        case _                  => false
      }
    }

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
