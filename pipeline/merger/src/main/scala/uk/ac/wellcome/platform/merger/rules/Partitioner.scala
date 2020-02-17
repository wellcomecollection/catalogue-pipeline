package uk.ac.wellcome.platform.merger.rules

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.model.PotentialMergedWork

trait Partitioner {
  protected def partitionWorks(works: Seq[BaseWork]): Option[Partition]
}

case class Partition(potentialMergedWork: PotentialMergedWork,
                     remainingWorks: Seq[BaseWork])

trait WorkTagPartitioner extends Partitioner {

  sealed trait WorkTag

  case object Target extends WorkTag
  case object Redirected extends WorkTag
  case object PassThrough extends WorkTag

  protected def tagWork(work: BaseWork): WorkTag

  def partitionWorks(works: Seq[BaseWork]): Option[Partition] = {
    val taggedWorks = works.groupBy(tagWork)
    val targetWorks = taggedWorks.get(Target).toList.flatten
    val redirectedWorks = taggedWorks.get(Redirected).toList.flatten
    val remaining = taggedWorks.get(PassThrough).toList.flatten
    (targetWorks, redirectedWorks) match {
      case (List(target: UnidentifiedWork), _ :: _) =>
        val redirectedTransformedWorks = redirectedWorks.flatMap {
          case work: TransformedBaseWork => Some(work)
          case _                         => None
        }
        Some(
          Partition(
            PotentialMergedWork(target, redirectedTransformedWorks),
            remaining))
      case _ => None
    }
  }
}
