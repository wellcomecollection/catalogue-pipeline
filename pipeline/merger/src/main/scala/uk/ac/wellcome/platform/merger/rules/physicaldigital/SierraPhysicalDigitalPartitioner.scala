package uk.ac.wellcome.platform.merger.rules.physicaldigital

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifierType,
  UnidentifiedWork
}
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

  private def isDigitalWork(work: UnidentifiedWork): Boolean =
    work.data.workType match {
      case None    => false
      case Some(t) => t.id == "v" && t.label == "E-books"
    }

  private def isSierraDigitalWork(work: UnidentifiedWork): Boolean =
    isSierraWork(work) && isDigitalWork(work)

  private def isSierraPhysicalWork(work: UnidentifiedWork): Boolean =
    isSierraWork(work) && !isDigitalWork(work)
}
