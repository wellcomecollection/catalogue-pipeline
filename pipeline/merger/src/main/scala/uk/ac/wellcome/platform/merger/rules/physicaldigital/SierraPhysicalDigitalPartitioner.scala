package uk.ac.wellcome.platform.merger.rules.physicaldigital

import uk.ac.wellcome.models.work.internal.{BaseWork, DigitalLocation, Identifiable, IdentifierType, Item, PhysicalLocation, Unidentifiable, UnidentifiedWork}
import uk.ac.wellcome.platform.merger.rules.WorkTagPartitioner

trait SierraPhysicalDigitalPartitioner extends WorkTagPartitioner {

  def tagWork(work: BaseWork): WorkTag =
    work match {
      case work: UnidentifiedWork if isSierraPhysicalWork(work) => Target
      case work: UnidentifiedWork if isSierraDigitalWork(work)  => Redirected
      case _                                                    => PassThrough
    }

  private def isSierraWork(work: UnidentifiedWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType(
      "sierra-system-number")

  private def isSierraDigitalWork(work: UnidentifiedWork): Boolean =
    isSierraWork(work) && isDigitalWork(work)

  private def isSierraPhysicalWork(work: UnidentifiedWork): Boolean =
    isSierraWork(work) && isPhysicalWork(work)

  /***
    * A Sierra digitised work will have a single item with a single [[DigitalLocation]]
    */
  private def isDigitalWork(work: UnidentifiedWork): Boolean =
    work.data.items match {
      case List(Unidentifiable(Item(List(_:DigitalLocation), _))) => true
      case _ => false
    }

  /***
    * A Sierra physical work will have a single item with one or more locations
    * of which at least one is a [[PhysicalLocation]]. It might have additional
    * [[DigitalLocation]]s if the Sierra record contains a dlnk
    */
  private def isPhysicalWork(work: UnidentifiedWork): Boolean =
    work.data.items match {
      case List(Identifiable(Item(locations, _), _, _,_)) if locations.exists(l => l.isInstanceOf[PhysicalLocation]) => true
      case _ => false
    }
}
