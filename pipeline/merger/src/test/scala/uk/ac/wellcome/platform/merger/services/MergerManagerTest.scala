package uk.ac.wellcome.platform.merger.services

import cats.data.State
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}
import WorkState.{Merged, Source}
import WorkFsm._

class MergerManagerTest extends AnyFunSpec with Matchers with WorkGenerators {

  it("performs a merge with a single work") {
    val work = sourceWork()

    val result = mergerManager.applyMerge(maybeWorks = List(Some(work)))

    result.mergedWorksWithTime(now) shouldBe List(
      work.transition[Merged]((Some(now), 1)))
  }

  it("performs a merge with multiple works") {
    val work = sourceWork()
    val otherWorks = sourceWorks(3)

    val works = (work +: otherWorks).map { Some(_) }.toList

    val result = mergerManager.applyMerge(maybeWorks = works)
    val resultWorks = result.mergedWorksWithTime(now)

    resultWorks.head shouldBe work.transition[Merged]((Some(now), 1))

    resultWorks.tail.zip(otherWorks).map {
      case (baseWork: Work[Merged], unmergedWork: Work.Visible[Source]) =>
        baseWork.sourceIdentifier shouldBe unmergedWork.sourceIdentifier

        val redirect = baseWork.asInstanceOf[Work.Redirected[Merged]]
        val redirectTarget =
          resultWorks.head.asInstanceOf[Work.Visible[Merged]]
        redirect.redirect.sourceIdentifier shouldBe redirectTarget.sourceIdentifier
    }
  }

  it("returns the works unmerged if any of the work entries are None") {
    val expectedWorks = sourceWorks(3)

    val maybeWorks = expectedWorks.map { Some(_) } ++ List(None)

    val result = mergerManager.applyMerge(maybeWorks = maybeWorks.toList)

    result.mergedWorksWithTime(now) should contain theSameElementsAs
      expectedWorks.map(_.transition[Merged]((Some(now), 1)))
  }

  val mergerRules = new Merger {

    /** Make every work a redirect to the first work in the list, and leave
      * the first work intact.
      */
    override def merge(works: Seq[Work[Source]]): MergerOutcome = {
      val outputWorks = works.head +: works.tail.map { work =>
        Work.Redirected[Source](
          state = Source(work.sourceIdentifier, work.state.modifiedTime),
          version = work.version,
          redirect = IdState.Identifiable(works.head.sourceIdentifier)
        )
      }
      MergerOutcome(
        resultWorks = outputWorks,
        imagesWithSources = Nil
      )
    }

    override def findTarget(
      works: Seq[Work[Source]]): Option[Work.Visible[Source]] =
      works.headOption.map(_.asInstanceOf[Work.Visible[Source]])

    override protected def createMergeResult(
      target: Work.Visible[Source],
      sources: Seq[Work[Source]]): State[MergeState, MergeResult] =
      State(
        _ =>
          (sources zip Stream.continually(true) toMap, MergeResult(target, Nil))
      )
  }

  val mergerManager = new MergerManager(mergerRules = mergerRules)
}
