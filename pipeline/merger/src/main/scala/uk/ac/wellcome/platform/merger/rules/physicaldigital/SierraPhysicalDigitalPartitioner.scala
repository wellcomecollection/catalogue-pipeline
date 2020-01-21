package uk.ac.wellcome.platform.merger.rules.physicaldigital

import uk.ac.wellcome.models.work.internal._
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
      case List(Item(Unidentifiable, _, List(_: DigitalLocation), _)) => true
      case _                                                          => false
    }

  /***
    * A Sierra work is a physical work if it has at least one item with one [[PhysicalLocation]].
    * The work can have multiple items with [[PhysicalLocation]]s and/or [[DigitalLocation]]s
    */
  private def isPhysicalWork(work: UnidentifiedWork): Boolean =
    work.data.items.exists(item =>
      item.locations.exists(l => l.isInstanceOf[PhysicalLocation]))

}
