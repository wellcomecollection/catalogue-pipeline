package uk.ac.wellcome.platform.merger.services

import cats.data.State
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}
import WorkState.{Identified, Merged}
import WorkFsm._

class MergerManagerTest extends AnyFunSpec with Matchers with WorkGenerators {

  it("performs a merge with a single work") {
    val work = identifiedWork()

    val result = mergerManager.applyMerge(maybeWorks = List(Some(work)))

    result.mergedWorksWithTime(now) shouldBe List(
      work.transition[Merged](Some(now)))
  }

  it("performs a merge with multiple works") {
    val work = identifiedWork()
    val otherWorks = identifiedWorks(3)

    val works = (work +: otherWorks).map { Some(_) }.toList

    val result = mergerManager.applyMerge(maybeWorks = works)
    val resultWorks = result.mergedWorksWithTime(now)

    resultWorks.head shouldBe work.transition[Merged](Some(now))

    resultWorks.tail.zip(otherWorks).map {
      case (baseWork: Work[Merged], unmergedWork: Work.Visible[Identified]) =>
        baseWork.sourceIdentifier shouldBe unmergedWork.sourceIdentifier

        val redirect = baseWork.asInstanceOf[Work.Redirected[Merged]]
        val redirectTarget =
          resultWorks.head.asInstanceOf[Work.Visible[Merged]]
        redirect.redirect.sourceIdentifier shouldBe redirectTarget.sourceIdentifier
    }
  }

  it("returns the works unmerged if any of the work entries are None") {
    val expectedWorks = identifiedWorks(3)

    val maybeWorks = expectedWorks.map { Some(_) } ++ List(None)

    val result = mergerManager.applyMerge(maybeWorks = maybeWorks.toList)

    result.mergedWorksWithTime(now) should contain theSameElementsAs
      expectedWorks.map(_.transition[Merged](Some(now)))
  }

  val mergerRules = new Merger {

    /** Make every work a redirect to the first work in the list, and leave
      * the first work intact.
      */
    override def merge(works: Seq[Work[Identified]]): MergerOutcome = {
      val outputWorks = works.head +: works.tail.map { work =>
        Work.Redirected[Identified](
          state = Identified(
            work.sourceIdentifier,
            work.state.canonicalId,
            work.state.modifiedTime),
          version = work.version,
          redirect = IdState.Identified(
            works.head.state.canonicalId,
            works.head.sourceIdentifier)
        )
      }
      MergerOutcome(
        resultWorks = outputWorks,
        imagesWithSources = Nil
      )
    }

    override def findTarget(
      works: Seq[Work[Identified]]): Option[Work.Visible[Identified]] =
      works.headOption.map(_.asInstanceOf[Work.Visible[Identified]])

    override protected def createMergeResult(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]): State[MergeState, MergeResult] =
      State(
        _ =>
          (sources zip Stream.continually(true) toMap, MergeResult(target, Nil))
      )
  }

  val mergerManager = new MergerManager(mergerRules = mergerRules)
}
