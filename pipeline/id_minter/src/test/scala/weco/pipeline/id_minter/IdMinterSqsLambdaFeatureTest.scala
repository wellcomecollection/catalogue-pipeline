package weco.pipeline.id_minter

import io.circe.Json
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda._
import weco.lambda.behaviours.LambdaBehavioursStringInStringOut
import weco.lambda.helpers.LambdaFixtures
import weco.pipeline.id_minter.utils.{DummyConfig, IdMinterSqsLambdaTestHelpers}

import scala.collection.mutable

trait IdMinterSqsLambdaBehaviours
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IdMinterSqsLambdaTestHelpers
    with WorkGenerators
    with LambdaFixtures
    with LambdaBehavioursStringInStringOut[DummyConfig] {

  def anInvocationMintingOneIdentifier(
    upstreamIndex: Map[String, Json],
    inputMessages: Seq[SQSLambdaMessage[String]],
    expectedSourceId: SourceIdentifier,
    downstreamDescription: String = "sends the minted canonicalid downstream"
  ): Unit = {
    val downstreamIndex = mutable.Map.empty[String, Work[Identified]]
    withIdMinterSQSLambdaBuilder(
      mergedIndex = upstreamIndex,
      identifiedIndex = downstreamIndex
    ) {
      idMinterSqsLambdaBuilder =>
        it should behave like aTotalSuccess(
          idMinterSqsLambdaBuilder,
          inputMessages,
          () => Seq(downstreamIndex.keys.loneElement),
          downstreamDescription = downstreamDescription
        )
    }
    it("sets the canonicalid of the document to the newly minted identifier") {
      val mintedId = downstreamIndex.keys.loneElement
      val identifiedWork = downstreamIndex(mintedId)
      identifiedWork.sourceIdentifier shouldBe expectedSourceId
      identifiedWork.state.canonicalId shouldBe CanonicalId(
        mintedId
      )
    }
  }
}

class IdMinterSqsLambdaFeatureTest extends IdMinterSqsLambdaBehaviours {

  describe("When there are multiple identical input source identifiers") {
    info(
      """multiple input messages with the same identifier
    only result in one record written and one message sent"""
    )
    val work = sourceWork()
    val messageCount = 5
    val messages: Seq[SQSLambdaMessage[String]] = (1 to messageCount).map {
      _ => SQSTestLambdaMessage(message = work.id)
    }
    it should behave like anInvocationMintingOneIdentifier(
      upstreamIndex = createIndex(List(work)),
      inputMessages = messages,
      expectedSourceId = work.sourceIdentifier,
      "sends one message downstream for each *unique* upstream message"
    )
  }

  describe("When a work to be processed is marked as Invisible") {
    val work = sourceWork().invisible()

    it should behave like anInvocationMintingOneIdentifier(
      upstreamIndex = createIndex(List(work)),
      inputMessages = Seq(SQSTestLambdaMessage(message = work.id)),
      expectedSourceId = work.sourceIdentifier
    )
  }

  describe("When a work to be processed is marked as Redirected") {
    val work = sourceWork()
      .redirected(
        redirectTarget =
          IdState.Identifiable(sourceIdentifier = createSourceIdentifier)
      )

    it should behave like anInvocationMintingOneIdentifier(
      upstreamIndex = createIndex(List(work)),
      inputMessages = Seq(SQSTestLambdaMessage(message = work.id)),
      expectedSourceId = work.sourceIdentifier
    )
  }

  describe("When not all messages correspond to a Work in the upstream index") {
    val work = sourceWork()
    val downstreamIndex = mutable.Map.empty[String, Work[Identified]]
    val goodMessage = SQSTestLambdaMessage(message = work.id)

    val badMessage =
      SQSTestLambdaMessage(message = "this_work_id_does_not_exist")

    withIdMinterSQSLambdaBuilder(
      mergedIndex = createIndex(List(work)),
      identifiedIndex = downstreamIndex
    ) {
      idMinterSqsLambdaBuilder =>
        it should behave like aPartialSuccess(
          idMinterSqsLambdaBuilder,
          Seq(goodMessage, badMessage),
          failingMessages = Seq(badMessage),
          outgoingMessageContent = () => Seq(downstreamIndex.keys.loneElement)
        )
    }
    it(
      "sets the canonicalid of the successful document to the newly minted identifier"
    ) {
      val mintedId = downstreamIndex.keys.loneElement
      val identifiedWork = downstreamIndex(mintedId)
      identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
      identifiedWork.state.canonicalId shouldBe CanonicalId(
        mintedId
      )
    }
  }
}
