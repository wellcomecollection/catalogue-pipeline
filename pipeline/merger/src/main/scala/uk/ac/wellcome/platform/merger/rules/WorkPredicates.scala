package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  IdentifierType,
  PhysicalLocation,
  TransformedBaseWork
}

object WorkPredicates {
  type WorkPredicate = TransformedBaseWork => Boolean

  val sierraWork: WorkPredicate = identifierTypeId("sierra-system-number")
  val metsWork: WorkPredicate = identifierTypeId("mets")
  val miroWork: WorkPredicate = identifierTypeId("miro-image-number")

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

  private def satisfiesAll(predicates: (TransformedBaseWork => Boolean)*)(
    work: TransformedBaseWork): Boolean = predicates.forall(_(work))

  implicit class WorkPredicateOps(val filterA: WorkPredicate) {
    def or(filterB: WorkPredicate): WorkPredicate =
      work => filterA(work) || filterB(work)
  }
}
