package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  IdentifierType,
  PhysicalLocation,
  TransformedBaseWork
}

object WorkFilters {
  type WorkFilter = TransformedBaseWork => Boolean

  val sierraWork: WorkFilter = identifierTypeId("sierra-system-number")
  val metsWork: WorkFilter = identifierTypeId("mets")
  val miroWork: WorkFilter = identifierTypeId("miro-image-number")

  val singleItemDigitalMets: WorkFilter = satisfiesAll(
    metsWork,
    singleItem,
    allDigitalLocations
  )

  val singleItemMiro: WorkFilter = satisfiesAll(miroWork, singleItem)

  val singleItemSierra: WorkFilter = satisfiesAll(sierraWork, singleItem)

  val physicalSierra: WorkFilter =
    satisfiesAll(sierraWork, physicalLocationExists)

  val digitalSierra: WorkFilter =
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

  private def singleItem(work: TransformedBaseWork): Boolean =
    work.data.items.size == 1

  private def identifierTypeId(id: String)(work: TransformedBaseWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType(id)

  private def satisfiesAll(predicates: (TransformedBaseWork => Boolean)*)(
    work: TransformedBaseWork): Boolean = predicates.forall(_(work))
}
