package uk.ac.wellcome.platform.merger.services

import cats.data.State
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifiableRedirect,
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}

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
      case (baseWork: BaseWork, unmergedWork: UnidentifiedWork) =>
        baseWork.sourceIdentifier shouldBe unmergedWork.sourceIdentifier

        val redirect = baseWork.asInstanceOf[UnidentifiedRedirectedWork]
        val redirectTarget = result.works.head.asInstanceOf[UnidentifiedWork]
        redirect.redirect.sourceIdentifier shouldBe redirectTarget.sourceIdentifier
    }
  }

  it("merges all available works and ignores None") {
    val expectedWorks = createUnidentifiedWorks(3)

    val maybeWorks = expectedWorks.map { Some(_) } ++ List(None)

    val result = mergerManager.applyMerge(maybeWorks = maybeWorks.toList)

    val redirectedWorks = expectedWorks.tail.map(
      work =>
        UnidentifiedRedirectedWork(
          sourceIdentifier = work.sourceIdentifier,
          version = work.version,
          redirect = IdentifiableRedirect(expectedWorks.head.sourceIdentifier)
      ))

    result.works.head shouldBe expectedWorks.head

    result.works.tail shouldBe redirectedWorks
  }

  val mergerRules = new Merger {

    /** Make every work a redirect to the first work in the list, and leave
      * the first work intact.
      */
    override def merge(works: Seq[TransformedBaseWork]): MergerOutcome =
      MergerOutcome(
        works = works.head +: works.tail.map { work =>
          UnidentifiedRedirectedWork(
            sourceIdentifier = work.sourceIdentifier,
            version = work.version,
            redirect = IdentifiableRedirect(works.head.sourceIdentifier)
          )
        },
        images = Nil
      )

    override def findTarget(
      works: Seq[TransformedBaseWork]): Option[UnidentifiedWork] =
      works.headOption.map(_.asInstanceOf[UnidentifiedWork])

    override protected def createMergeResult(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): MergeState =
      State(_ => (sources.toSet, MergeResult(target, Nil)))
  }

  val mergerManager = new MergerManager(mergerRules = mergerRules)
}
