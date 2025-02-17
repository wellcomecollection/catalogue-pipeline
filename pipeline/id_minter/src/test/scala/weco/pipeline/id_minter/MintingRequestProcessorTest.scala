package weco.pipeline.id_minter

import org.scalatest.LoneElement
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline_storage.Indexer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MintingRequestProcessorTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with LoneElement
    with IntegrationPatience
    with WorkGenerators {
  class MockIndexer(
    applyResponse: Either[Seq[Work[Identified]], Seq[Work[Identified]]]
  ) extends Indexer[Work[Identified]] {

    override def init(): Future[Unit] = Future.successful(Unit)

    override def apply(
      documents: Seq[Work[Identified]]
    ): Future[Either[Seq[Work[Identified]], Seq[Work[Identified]]]] =
      Future.successful(applyResponse)
  }

  class MockMinter(
    applyResponse: Iterable[Either[String, Work[Identified]]]
  ) extends IdListMinter {

    override def processSourceIds(identifiers: Seq[String])(
      implicit ec: ExecutionContext
    ): Future[Iterable[Either[String, Work[Identified]]]] =
      Future.successful(applyResponse)
  }

  it("does nothing if there is nothing to do") {
    val work = identifiedWork()
    val minter = new MintingRequestProcessor(
      minter = new MockMinter(Seq(Left(work.sourceIdentifier.toString))),
      workIndexer = new MockIndexer(Left(Seq(work)))
    )
    whenReady(minter.process(Nil)) {
      result =>
        result.failures shouldBe empty
        result.successes shouldBe empty
    }
  }
  describe("when everything is OK") {
    val works = identifiedWorks(3)
    val minter = new MintingRequestProcessor(
      minter = new MockMinter(works.map(Right(_))),
      workIndexer = new MockIndexer(Right(works))
    )
    whenReady(minter.process(works.map(_.sourceIdentifier.toString))) {
      result =>
        it("does not report any failures") {
          result.failures shouldBe empty
        }
        it("returns the canonical IDs of all the successes") {
          result.successes should contain theSameElementsAs works.map(_.id)
        }
    }
  }
  describe("when there are failures in retrieval or minting") {
    val successfulWork = identifiedWork()
    val minter = new MintingRequestProcessor(
      minter =
        new MockMinter(Seq(Left("abc"), Left("def"), Right(successfulWork))),
      workIndexer = new MockIndexer(Right(Nil))
    )
    whenReady(minter.process(Seq("abc", "def", "ghi"))) {
      result =>
        it(
          "returns the source identifiers of the records that failed to mint new ids"
        ) {
          result.failures should contain theSameElementsAs Seq("abc", "def")
        }
        it("returns the canonical identifiers of any successes") {
          result.successes.loneElement shouldBe successfulWork.id
        }
    }
  }

  describe("when some records fail to be indexed") {
    val works = identifiedWorks(3)
    val minter = new MintingRequestProcessor(
      minter = new MockMinter(works.map(Right(_))),
      workIndexer = new MockIndexer(Left(works.tail))
    )
    val successfulWork = works.head

    whenReady(minter.process(works.map(_.sourceIdentifier.toString))) {
      result =>
        it(
          "returns the source identifiers of the records that could not be stored"
        ) {
          result.failures should contain theSameElementsAs works.tail.map(
            _.sourceIdentifier.toString
          )
        }
        it("returns the canonical identifiers of any successes") {
          result.successes.loneElement shouldBe successfulWork.id
        }
    }
  }

  describe("when there are failures in retrieval/minting and indexing") {

    val works = identifiedWorks(4)
    val minterSuccess = works.tail
    val minterFail = works.head
    val indexerFail = minterSuccess.head
    val indexerSuccess = minterSuccess.tail
    val minter = new MintingRequestProcessor(
      minter = new MockMinter(
        minterSuccess.map(Right(_)) :+ Left(
          minterFail.sourceIdentifier.toString
        )
      ),
      workIndexer = new MockIndexer(Left(Seq(indexerFail)))
    )
    whenReady(minter.process(works.map(_.sourceIdentifier.toString))) {
      result =>
        it("returns the source IDs for any records that fail for any reason") {
          result.failures should contain theSameElementsAs Seq(
            minterFail,
            indexerFail
          )
            .map(_.sourceIdentifier.toString)
        }
        it(
          "returns the canonical ids for any successfully processed records"
        ) {
          result.successes should contain theSameElementsAs indexerSuccess
            .map(_.id)
        }
    }
  }
}
