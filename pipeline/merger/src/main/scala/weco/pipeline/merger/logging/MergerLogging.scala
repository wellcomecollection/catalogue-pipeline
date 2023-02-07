package weco.pipeline.merger.logging

import grizzled.slf4j.Logging
import cats.data.NonEmptyList
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.models.ImageDataWithSource

trait MergerLogging extends Logging {
  def describeWork(work: Work[_]): String =
    s"(id=${work.sourceIdentifier})"

  def describeWorks(works: Seq[Work[_]]): String =
    s"[${works.map(describeWork).mkString(",")}]"

  def describeWorks(works: NonEmptyList[Work[_]]): String =
    describeWorks(works.toList)

  def describeImage(imageDataWithSource: ImageDataWithSource): String =
    s"(id=${imageDataWithSource.imageData.id.sourceIdentifier})"

  def describeImages(images: Seq[ImageDataWithSource]): String =
    s"[${images.map(describeImage).mkString(",")}]"

  def describeMergeSet(target: Work[_], sources: Seq[Work[_]]): String =
    s"target${describeWork(target)} with sources${describeWorks(sources)}"

  def describeMergeOutcome(
    target: Work[_],
    redirected: Seq[Work[_]],
    remaining: Seq[Work[_]]
  ): String =
    s"target${describeWork(target)} with redirected${describeWorks(redirected)} and remaining${describeWorks(remaining)}"
}
