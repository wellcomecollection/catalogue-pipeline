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

trait WorkPairMerger {
  protected def mergeAndRedirectWorkPair(
    firstWork: UnidentifiedWork,
    secondWork: TransformedBaseWork): Option[MergedWork]
}
