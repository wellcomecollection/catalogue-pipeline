package uk.ac.wellcome.platform.merger.logging

import grizzled.slf4j.Logging
import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal._

trait MergerLogging extends Logging {
  def describeWork(work: Work[_]): String =
    s"(id=${work.sourceIdentifier.value})"

  def describeWorks(works: Seq[Work[_]]): String =
    s"[${works.map(describeWork).mkString(",")}]"

  def describeWorks(works: NonEmptyList[Work[_]]): String =
    describeWorks(works.toList)

  def describeImage(image: BaseImage[DataState.Unidentified]): String =
    s"(id=${image.id})"

  def describeImages(images: Seq[BaseImage[DataState.Unidentified]]): String =
    s"[${images.map(describeImage).mkString(",")}]"

  def describeMergeSet(target: Work[_],
                       sources: Seq[Work[_]]): String =
    s"target${describeWork(target)} with sources${describeWorks(sources)}"

  def describeMergeOutcome(target: Work[_],
                           redirected: Seq[Work[_]],
                           remaining: Seq[Work[_]]): String =
    s"target${describeWork(target)} with redirected${describeWorks(redirected)} and remaining${describeWorks(remaining)}"
}
