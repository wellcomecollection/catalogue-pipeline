package weco.lambda.behaviours

import io.circe.Decoder
import org.scalatest.LoneElement
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.lambda.helpers.DownstreamHelper
import weco.lambda._
import weco.lambda.matchers.LambdaResultMatchers

trait LambdaBehaviours[
  T,
  Config <: ApplicationConfig,
  OutputMessageType,
  ComparisonType
] extends Matchers
    with LambdaResultMatchers[T]
    with DownstreamHelper
    with ScalaFutures
    with LoneElement {
  this: AnyFunSpec =>
  protected type LambdaApp = SQSBatchResponseLambdaApp[T, Config]
  protected type IncomingMessage = SQSLambdaMessage[T]
  protected implicit val outputDecoder: Decoder[OutputMessageType]

  protected def convertForComparison(
    results: Seq[OutputMessageType]
  ): Seq[ComparisonType]

  def aFailingInvocation(
    lambdaBuilder: Downstream => LambdaApp,
    messages: Seq[IncomingMessage]
  ): Unit = {
    val downstream = new MemoryDownstream
    whenReady(lambdaBuilder(downstream).processMessages(messages = messages)) {
      results: Seq[SQSLambdaMessageResult] =>
        it(
          "returns BatchItemFailure responses for each input message"
        ) {
          results should haveTheSameIdsAs(messages)
          all(results) shouldBe a[SQSLambdaMessageFailedRetryable]
        }
        it("does not notify downstream") {
          downstream.msgSender.getMessages[OutputMessageType] shouldBe empty
        }
    }
  }

  def aPartialSuccess(
    lambdaBuilder: Downstream => LambdaApp,
    messages: Seq[IncomingMessage],
    failingMessages: Seq[IncomingMessage],
    outputs: Seq[ComparisonType]
  ): Unit = {

    val downstream = new MemoryDownstream

    whenReady(
      lambdaBuilder(downstream).processMessages(messages = messages)
    ) {
      results: Seq[SQSLambdaMessageResult] =>
        it("returns BatchItemFailure responses only for failing ids") {
          results should haveTheSameIdsAs(failingMessages)
          all(results) shouldBe a[SQSLambdaMessageFailedRetryable]
        }
        it("notifies downstream only for successful ids") {
          convertForComparison(
            downstream.msgSender
              .getMessages[OutputMessageType]
          ) should contain theSameElementsAs outputs
        }
    }
  }

  def aTotalSuccess(
    lambdaBuilder: Downstream => LambdaApp,
    messages: Seq[IncomingMessage],
    outputs: Seq[ComparisonType]
  ): Unit = {
    val downstream = new MemoryDownstream
    whenReady(
      lambdaBuilder(downstream).processMessages(messages = messages)
    ) {
      response =>
        it("returns no results") {
          response shouldBe empty
        }
        it("sends all the identifiers downstream") {
          convertForComparison(
            downstream.msgSender
              .getMessages[OutputMessageType]
          ) should contain theSameElementsAs outputs
        }
    }
  }
}
