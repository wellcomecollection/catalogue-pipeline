package uk.ac.wellcome.platform.merger.services

import cats.data.State
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}
import WorkState.Unidentified

class MergerManagerTest extends AnyFunSpec with Matchers with WorksGenerators {

  it("performs a merge with a single work") {
    val work = createUnidentifiedWork

    val result = mergerManager.applyMerge(maybeWorks = List(Some(work)))

    result.works shouldBe List(work)
  }

  it("performs a merge with multiple works") {
    val work = createUnidentifiedWork
    val otherWorks = createUnidentifiedWorks(3)

    val works = (work +: otherWorks).map { Some(_) }.toList

    val result = mergerManager.applyMerge(maybeWorks = works)

    result.works.head shouldBe work

    result.works.tail.zip(otherWorks).map {
      case (
          baseWork: Work[Unidentified],
          unmergedWork: Work.Standard[Unidentified]) =>
        baseWork.sourceIdentifier shouldBe unmergedWork.sourceIdentifier

        val redirect = baseWork.asInstanceOf[Work.Redirected[Unidentified]]
        val redirectTarget =
          result.works.head.asInstanceOf[Work.Standard[Unidentified]]
        redirect.redirect.sourceIdentifier shouldBe redirectTarget.sourceIdentifier
    }
  }

  it("returns the works unmerged if any of the work entries are None") {
    val expectedWorks = createUnidentifiedWorks(3)

    val maybeWorks = expectedWorks.map { Some(_) } ++ List(None)

    val result = mergerManager.applyMerge(maybeWorks = maybeWorks.toList)

    result.works should contain theSameElementsAs expectedWorks
  }

  val mergerRules = new Merger {

    /** Make every work a redirect to the first work in the list, and leave
      * the first work intact.
      */
    override def merge(works: Seq[Work[Unidentified]]): MergerOutcome =
      MergerOutcome(
        works = works.head +: works.tail.map { work =>
          Work.Redirected[Unidentified](
            state = Unidentified(work.sourceIdentifier),
            version = work.version,
            redirect = IdState.Identifiable(works.head.sourceIdentifier)
          )
        },
        images = Nil
      )

    override def findTarget(
      works: Seq[Work[Unidentified]]): Option[Work.Standard[Unidentified]] =
      works.headOption.map(_.asInstanceOf[Work.Standard[Unidentified]])

    override protected def createMergeResult(
      target: Work.Standard[Unidentified],
      sources: Seq[Work[Unidentified]]): MergeState =
      State(_ => (sources.toSet, MergeResult(target, Nil)))
  }

  val mergerManager = new MergerManager(mergerRules = mergerRules)
}
