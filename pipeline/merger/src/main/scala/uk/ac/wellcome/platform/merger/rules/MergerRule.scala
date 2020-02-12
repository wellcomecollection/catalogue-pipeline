package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.model.{MergedWork, PotentialMergedWork}

import scala.annotation.tailrec

trait MergerRule { this: Partitioner with WorkPairMerger =>

  final def mergeAndRedirectWorks(works: Seq[BaseWork]): Seq[BaseWork] =
    partitionWorks(works) match {
      case Some(
          Partition(PotentialMergedWork(target, worksToRedirect), remaining)) =>
        foldWorkPairs(target, worksToRedirect)
          .map(result => updateVersion(result) ++ remaining)
          .getOrElse(works)
      case None => works
    }

  @tailrec
  private def foldWorkPairs(
    target: UnidentifiedWork,
    toRedirect: Seq[TransformedBaseWork],
    redirected: Seq[UnidentifiedRedirectedWork] = Nil): Option[MergedWork] = {
    if (toRedirect.isEmpty) {
      Some(MergedWork(target, redirected))
    } else {
      mergeAndRedirectWorkPair(target, toRedirect.head) match {
        case Some(MergedWork(nextTarget, Seq(nextRedirected))) =>
          foldWorkPairs(
            nextTarget,
            toRedirect.tail,
            nextRedirected +: redirected
          )
        case _ => None
      }
    }
  }

  private def updateVersion(mergedWork: MergedWork): Seq[BaseWork] =
    mergedWork match {
      case MergedWork(work, redirectedWorks) =>
        work.withData(_.copy(merged = true)) +: redirectedWorks
    }
}

trait WorkPairMerger {
  protected def mergeAndRedirectWorkPair(
    firstWork: UnidentifiedWork,
    secondWork: TransformedBaseWork): Option[MergedWork]
}
