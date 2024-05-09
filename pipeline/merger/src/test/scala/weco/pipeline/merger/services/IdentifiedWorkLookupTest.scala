package weco.pipeline.merger.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.matcher.models.WorkIdentifier
import weco.pipeline_storage.memory.MemoryRetriever

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IdentifiedWorkLookupTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with WorkGenerators {

  it("fetches a single Work") {
    val work = identifiedWork()

    val retriever = new MemoryRetriever[Work[Identified]](
      index = mutable.Map(work.id -> work)
    )

    whenReady(fetchAllWorks(retriever = retriever, work)) {
      _ shouldBe Seq(Some(work))
    }
  }

  it("throws an error if asked to fetch a non-existent work") {
    val work = identifiedWork()

    val retriever = new MemoryRetriever[Work[Identified]]()

    whenReady(fetchAllWorks(retriever = retriever, work).failed) {
      _ shouldBe a[Exception]
    }
  }

  it("returns None if the stored version has a higher version") {
    val oldWork = identifiedWork()
    val newWork = oldWork.withVersion(oldWork.version + 1)

    val retriever = new MemoryRetriever[Work[Identified]](
      index = mutable.Map(newWork.id -> newWork)
    )

    whenReady(fetchAllWorks(retriever = retriever, oldWork)) {
      _ shouldBe Seq(None)
    }
  }

  it("gets a mixture of works as appropriate") {
    val unchangedWorks = identifiedWorks(count = 3)
    val outdatedWorks = identifiedWorks(count = 2)
    val updatedWorks = outdatedWorks.map {
      work =>
        work.withVersion(work.version + 1)
    }

    val lookupWorks = unchangedWorks ++ outdatedWorks
    val storedWorks = unchangedWorks ++ updatedWorks

    val retriever = new MemoryRetriever[Work[Identified]](
      index = mutable.Map(storedWorks.map {
        w =>
          w.id -> w
      }: _*)
    )

    val expectedLookupResult =
      unchangedWorks.map { Some(_) } ++ (4 to 5).map {
        _ =>
          None
      }

    whenReady(fetchAllWorks(retriever = retriever, lookupWorks: _*)) {
      _ shouldBe expectedLookupResult
    }
  }

  private def fetchAllWorks(
    retriever: MemoryRetriever[Work[Identified]],
    works: Work[Identified]*
  ): Future[Seq[Option[Work[Identified]]]] = {
    val identifiedLookup = new IdentifiedWorkLookup(retriever)

    val workIdentifiers = works
      .map {
        w =>
          WorkIdentifier(w)
      }

    identifiedLookup.fetchAllWorks(workIdentifiers)
  }
}
