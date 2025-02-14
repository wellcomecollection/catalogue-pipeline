package weco.pipeline.id_minter

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

  it("returns any failed ids from the minter") {
    val minter = new MintingRequestProcessor(
      minter = new MockMinter(Seq(Left("abc"), Left("def"))),
      workIndexer = new MockIndexer(Right(Nil))
    )
    whenReady(minter.process(Seq("abc", "def", "ghi"))) {
      result =>
        result should contain theSameElementsAs Seq("abc", "def")
    }

  }
  it("returns indexer failures") {
    val works = identifiedWorks(3)
    val minter = new MintingRequestProcessor(
      minter = new MockMinter(works.map(Right(_))),
      workIndexer = new MockIndexer(Left(works.tail))
    )
    whenReady(minter.process(works.map(_.sourceIdentifier.toString))) {
      result =>
        result should contain theSameElementsAs works.tail.map(
          _.sourceIdentifier.toString
        )
    }
  }
  it("returns all failures for any reason") {

    val works = identifiedWorks(4)
    val minterSuccess = works.tail
    val minterFail = works.head
    val indexerSuccess = minterSuccess.tail
    val indexerFail = minterSuccess.head

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
        result should contain theSameElementsAs Seq(minterFail, indexerFail)
          .map(_.sourceIdentifier.toString)
    }
  }
}
