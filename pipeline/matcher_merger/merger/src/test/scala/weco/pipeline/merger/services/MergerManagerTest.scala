package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.merger.models.{MergeResult, MergerOutcome}

class MergerManagerTest extends AnyFunSpec with Matchers with WorkGenerators {

  it("performs a merge with a single work") {
    val work = identifiedWork()

    val result = mergerManager.applyMerge(maybeWorks = List(Some(work)))

    result.mergedWorksWithTime(now) shouldBe List(work.transition[Merged](now))
  }

  it("performs a merge with multiple works") {
    val work = identifiedWork()
    val otherWorks = identifiedWorks(3)

    val works = (work +: otherWorks).map { Some(_) }

    val result = mergerManager.applyMerge(maybeWorks = works)
    val resultWorks = result.mergedWorksWithTime(now)

    resultWorks.head shouldBe work.transition[Merged](now)

    resultWorks.tail.zip(otherWorks).map {
      case (baseWork: Work[Merged], unmergedWork: Work.Visible[Identified]) =>
        baseWork.sourceIdentifier shouldBe unmergedWork.sourceIdentifier

        val redirect = baseWork.asInstanceOf[Work.Redirected[Merged]]
        val redirectTarget =
          resultWorks.head.asInstanceOf[Work.Visible[Merged]]
        redirect.redirectTarget.sourceIdentifier shouldBe redirectTarget.sourceIdentifier
    }
  }

  it("returns the works unmerged if any of the work entries are None") {
    val expectedWorks = identifiedWorks(3)

    val maybeWorks = expectedWorks.map { Some(_) } ++ List(None)

    val result = mergerManager.applyMerge(maybeWorks = maybeWorks.toList)

    result.mergedWorksWithTime(now) should contain theSameElementsAs
      expectedWorks.map(_.transition[Merged](now))
  }

  val merger = new Merger {

    /** Make every work a redirect to the first work in the list, and leave the
      * first work intact.
      */
    override def merge(works: Seq[Work[Identified]]): MergerOutcome = {
      val outputWorks = works.head +: works.tail.map {
        work =>
          Work.Redirected[Identified](
            state = Identified(
              sourceIdentifier = work.sourceIdentifier,
              canonicalId = work.state.canonicalId,
              sourceModifiedTime = work.state.sourceModifiedTime,
              internalWorkStubs = work.state.internalWorkStubs
            ),
            version = work.version,
            redirectTarget = IdState.Identified(
              works.head.state.canonicalId,
              works.head.sourceIdentifier
            )
          )
      }
      MergerOutcome(
        resultWorks = outputWorks,
        imagesWithSources = Nil
      )
    }

    override def findTarget(
      works: Seq[Work[Identified]]
    ): Option[Work.Visible[Identified]] =
      works.headOption.map(_.asInstanceOf[Work.Visible[Identified]])

    override protected def createMergeResult(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]
    ): (Seq[Work[Identified]], MergeResult) =
      (sources, MergeResult(target, Nil))
  }

  val mergerManager = new MergerManager(merger)

}
