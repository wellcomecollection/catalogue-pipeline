package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.model.{MergedWork, PotentialMergedWork}

trait MergerRule { this: Partitioner with WorkPairMerger =>

  final def mergeAndRedirectWorks(works: Seq[BaseWork]): Seq[BaseWork] =
    partitionWorks(works) match {
      case Some(
          Partition(PotentialMergedWork(target, redirectedWork), remaining)) =>
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

trait WorkPairMerger {
  protected def mergeAndRedirectWorkPair(
    firstWork: UnidentifiedWork,
    secondWork: TransformedBaseWork): Option[MergedWork]
}
