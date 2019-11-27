package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.model.MergedWork

trait MergerRule { this: Partitioner with WorkPairMerger =>

  def mergeAndRedirectWorks(works: Seq[BaseWork]): Seq[BaseWork] =
    partitionWorks(works) match {
      case Some(Partition(PotentialMergedWork(target, redirectedWork), remaining)) =>
        mergeAndRedirectWorkPair(target, redirectedWork)
          .map(result => updateVersion(result) ++ remaining)
          .getOrElse(works)
      case None => works
    }

  private def updateVersion(mergedWork: MergedWork): Seq[BaseWork] =
    mergedWork match {
      case MergedWork(work, redirectedWork) =>
        List(
          work.withData(_.copy(merged = true)),
          redirectedWork
        )
    }
}

case class PotentialMergedWork(target: UnidentifiedWork,
                               redirectedWork: TransformedBaseWork)

case class Partition(potentialMergedWork: PotentialMergedWork,
                     remainingWorks: Seq[BaseWork])

trait Partitioner {
  protected def partitionWorks(works: Seq[BaseWork]): Option[Partition]
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
    val redirectedWorks = taggedWorks.get(Redirected).toList.flatten
    val remaining = taggedWorks.get(PassThrough).toList.flatten
    (targetWorks, redirectedWorks) match {
      case (List(target: UnidentifiedWork), List(redirected: TransformedBaseWork)) =>
        Some(Partition(PotentialMergedWork(target, redirected), remaining))
      case _ => None
    }
  }
}

trait WorkPairMerger {
  protected def mergeAndRedirectWorkPair(
    firstWork: UnidentifiedWork,
    secondWork: TransformedBaseWork): Option[MergedWork]
}
