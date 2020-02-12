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

trait WorkTagPairPartitioner extends WorkTagPartitioner {
  override def partitionWorks(works: Seq[BaseWork]): Option[Partition] =
    super.partitionWorks(works).flatMap {
      case Partition(PotentialMergedWork(_, worksToRedirect), _)
          if worksToRedirect.size != 1 =>
        None
      case partition => Some(partition)
    }
}

trait WorkTagPartitioner extends Partitioner {

  sealed trait WorkTag

  case object Target extends WorkTag
  case object Redirected extends WorkTag
  case object PassThrough extends WorkTag

  protected def tagWork(work: BaseWork): WorkTag

  def partitionWorks(works: Seq[BaseWork]): Option[Partition] = {
    val taggedWorks = works.groupBy(tagWork)
    val targetWorks = taggedWorks.get(Target).toList.flatten
    val worksToRedirect = taggedWorks.get(Redirected).toList.flatten
    val remaining = taggedWorks.get(PassThrough).toList.flatten
    (targetWorks, worksToRedirect) match {
      case (List(target: UnidentifiedWork), _ :: _) =>
        val transformedWorksToRedirect = worksToRedirect.flatMap {
          case work: TransformedBaseWork => Some(work)
          case _                         => None
        }
        Some(
          Partition(
            PotentialMergedWork(target, transformedWorksToRedirect),
            remaining))
      case _ => None
    }
  }
}
