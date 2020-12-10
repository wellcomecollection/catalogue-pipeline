package uk.ac.wellcome.platform.merger.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import WorkState.Source
import uk.ac.wellcome.pipeline_storage.MemoryRetriever

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IdentifiedWorkLookupTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with WorkGenerators {

  it("fetches a single Work") {
    val work = sourceWork()

    val retriever = new MemoryRetriever[Work[Source]](
      index = mutable.Map(work.id -> work)
    )

    whenReady(fetchAllWorks(retriever = retriever, work)) {
      _ shouldBe Seq(Some(work))
    }
  }

  it("throws an error if asked to fetch a non-existent work") {
    val work = sourceWork()

    val retriever = new MemoryRetriever[Work[Source]]()

    whenReady(fetchAllWorks(retriever = retriever, work).failed) {
      _ shouldBe a[Exception]
    }
  }

  it("returns None if asked to fetch a Work without a version") {
    val work = sourceWork().withVersion(0)
    val workId = WorkIdentifier(work.sourceIdentifier.toString, version = None)

    val retriever = new MemoryRetriever[Work[Source]](
      index = mutable.Map(work.id -> work)
    )

    val sourceWorkLookup = new IdentifiedWorkLookup(retriever)

    whenReady(sourceWorkLookup.fetchAllWorks(workIdentifiers = List(workId))) {
      _ shouldBe Seq(None)
    }
  }

  it("returns None if the stored version has a higher version") {
    val oldWork = sourceWork()
    val newWork = oldWork.withVersion(oldWork.version + 1)

    val retriever = new MemoryRetriever[Work[Source]](
      index = mutable.Map(newWork.id -> newWork)
    )

    whenReady(fetchAllWorks(retriever = retriever, oldWork)) {
      _ shouldBe Seq(None)
    }
  }

  it("gets a mixture of works as appropriate") {
    val unchangedWorks = sourceWorks(count = 3)
    val outdatedWorks = sourceWorks(count = 2)
    val updatedWorks = outdatedWorks.map { work =>
      work.withVersion(work.version + 1)
    }

    val lookupWorks = unchangedWorks ++ outdatedWorks
    val storedWorks = unchangedWorks ++ updatedWorks

    val retriever = new MemoryRetriever[Work[Source]](
      index = mutable.Map(storedWorks.map { w =>
        w.id -> w
      }: _*)
    )

    val expectedLookupResult =
      unchangedWorks.map { Some(_) } ++ (4 to 5).map { _ =>
        None
      }

    whenReady(fetchAllWorks(retriever = retriever, lookupWorks: _*)) {
      _ shouldBe expectedLookupResult
    }
  }

  private def fetchAllWorks(
    retriever: MemoryRetriever[Work[Source]],
    works: Work[Source]*): Future[Seq[Option[Work[Source]]]] = {
    val sourceLookup = new IdentifiedWorkLookup(retriever)

    val workIdentifiers = works
      .map { w =>
        WorkIdentifier(w)
      }

    sourceLookup.fetchAllWorks(workIdentifiers)
  }
}
