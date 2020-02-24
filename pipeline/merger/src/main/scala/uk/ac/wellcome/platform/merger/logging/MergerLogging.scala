package uk.ac.wellcome.platform.merger.logging

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.BaseWork

trait MergerLogging extends Logging {
  def describeWork(work: BaseWork): String =
    s"(id=${work.sourceIdentifier.value})"

  def describeWorks(works: Seq[BaseWork]): String =
    s"[${works.map(describeWork).mkString(",")}]"

  def describeMergeSet(target: BaseWork, sources: Seq[BaseWork]): String =
    s"target${describeWork(target)} with sources${describeWorks(sources)}"

  def describeMergeOutcome(target: BaseWork,
                           redirected: Seq[BaseWork],
                           remaining: Seq[BaseWork]): String =
    s"target${describeWork(target)} with redirected${describeWorks(redirected)} and remaining${describeWorks(remaining)}"
}
