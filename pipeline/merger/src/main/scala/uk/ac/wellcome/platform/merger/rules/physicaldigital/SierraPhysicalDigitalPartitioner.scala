package uk.ac.wellcome.platform.merger.rules.physicaldigital

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifierType,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.{Partition, Partitioner, PotentialMergedWork}

trait SierraPhysicalDigitalPartitioner extends Partitioner {

  def partitionWorks(works: Seq[BaseWork]): Option[Partition] = {
    val groupedWorks = works.groupBy {
      case work: UnidentifiedWork if isSierraPhysicalWork(work) =>
        workType.SierraPhysicalWork
      case work: UnidentifiedWork if isSierraDigitalWork(work) =>
        workType.SierraDigitalWork
      case _ => workType.OtherWork
    }

    val physicalWorks =
      groupedWorks.get(workType.SierraPhysicalWork).toList.flatten
    val digitalWorks =
      groupedWorks.get(workType.SierraDigitalWork).toList.flatten
    val otherWorks = groupedWorks.get(workType.OtherWork).toList.flatten

    (physicalWorks, digitalWorks) match {
      case (
          List(physicalWork: UnidentifiedWork),
          List(digitalWork: UnidentifiedWork)) =>
        Some(Partition(PotentialMergedWork(physicalWork, digitalWork), otherWorks))
      case _ => None
    }
  }

  private object workType extends Enumeration {
    val SierraDigitalWork, SierraPhysicalWork, OtherWork = Value
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
