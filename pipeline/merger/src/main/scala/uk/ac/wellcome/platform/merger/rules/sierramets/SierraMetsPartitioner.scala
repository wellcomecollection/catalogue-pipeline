package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifierType,
  UnidentifiedInvisibleWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.{Partition, Partitioner, PotentialMergedWork}

class SierraMetsPartitioner extends Partitioner {

  def partitionWorks(works: Seq[BaseWork]): Option[Partition] = {
    val groupedWorks = works.groupBy {
      case work: UnidentifiedWork if isSierraWork(work) =>
        workType.SierraWork
      case work: UnidentifiedInvisibleWork if isMetsWork(work) =>
        workType.MetsWork
      case _ => workType.OtherWork
    }

    val sierraWorks =
      groupedWorks.get(workType.SierraWork).toList.flatten
    val metsWorks =
      groupedWorks.get(workType.MetsWork).toList.flatten
    val otherWorks = groupedWorks.get(workType.OtherWork).toList.flatten

    (sierraWorks, metsWorks) match {
      case (
          List(physicalWork: UnidentifiedWork),
          List(metsWork: UnidentifiedInvisibleWork)) =>
        Some(Partition(PotentialMergedWork(physicalWork, metsWork), otherWorks))
      case _ => None
    }
  }

  private object workType extends Enumeration {
    val SierraWork, MetsWork, OtherWork = Value
  }
  private def isSierraWork(work: UnidentifiedWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType(
      "sierra-system-number")

  private def isMetsWork(work: UnidentifiedInvisibleWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType("mets")
}
