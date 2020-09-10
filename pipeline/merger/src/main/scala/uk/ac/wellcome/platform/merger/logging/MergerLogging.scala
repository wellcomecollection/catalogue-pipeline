package uk.ac.wellcome.platform.merger.logging

import grizzled.slf4j.Logging
import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal.{
  BaseImage,
  BaseWork,
  IdState
}

trait MergerLogging extends Logging {
  def describeWork(work: BaseWork): String =
    s"(id=${work.sourceIdentifier.value})"

  def describeWorks(works: Seq[BaseWork]): String =
    s"[${works.map(describeWork).mkString(",")}]"

  def describeWorks(works: NonEmptyList[BaseWork]): String =
    describeWorks(works.toList)

  def describeImage(image: BaseImage[IdState.Identifiable, IdState.Unminted]): String =
    s"(id=${image.id})"

  def describeImages(images: Seq[BaseImage[IdState.Identifiable, IdState.Unminted]]): String =
    s"[${images.map(describeImage).mkString(",")}]"

  def describeMergeSet(target: BaseWork, sources: Seq[BaseWork]): String =
    s"target${describeWork(target)} with sources${describeWorks(sources)}"

  def describeMergeOutcome(target: BaseWork,
                           redirected: Seq[BaseWork],
                           remaining: Seq[BaseWork]): String =
    s"target${describeWork(target)} with redirected${describeWorks(redirected)} and remaining${describeWorks(remaining)}"
}
