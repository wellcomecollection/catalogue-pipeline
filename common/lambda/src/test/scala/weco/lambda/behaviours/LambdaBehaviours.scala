package weco.lambda.behaviours

import io.circe.Decoder
import org.scalatest.LoneElement
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.lambda._
import weco.lambda.helpers.MemoryDownstream
import weco.lambda.matchers.LambdaResultMatchers

trait LambdaBehaviours[
  InputMessageType,
  Config <: ApplicationConfig,
  OutputMessageType,
  ComparisonType
] extends Matchers
    with LambdaResultMatchers[InputMessageType]
    with MemoryDownstream
    with ScalaFutures
    with LoneElement {
  this: AnyFunSpec =>
  protected type LambdaApp = SQSBatchResponseLambdaApp[InputMessageType, Config]
  protected type IncomingMessage = SQSLambdaMessage[InputMessageType]
  protected implicit val outputDecoder: Decoder[OutputMessageType]

  protected def convertForComparison(
    results: Seq[OutputMessageType]
  ): Seq[ComparisonType]

  protected def getMessages(
    downstream: MemorySNSDownstream
  ): Seq[OutputMessageType] =
    downstream.msgSender.getMessages[OutputMessageType]

  def aFailingInvocation(
    lambdaBuilder: Downstream => LambdaApp,
    messages: Seq[IncomingMessage]
  ): Unit = {
    val downstream = new MemorySNSDownstream
    whenReady(lambdaBuilder(downstream).processMessages(messages = messages)) {
      results: Seq[SQSLambdaMessageResult] =>
        it(
          "returns BatchItemFailure responses for each input message"
        ) {
          results should haveTheSameIdsAs(messages)
          all(results) shouldBe a[SQSLambdaMessageFailedRetryable]
        }
        it("does not notify downstream") {
          getMessages(downstream) shouldBe empty
        }
    }
  }

  def aPartialSuccess(
    lambdaBuilder: Downstream => LambdaApp,
    messages: Seq[IncomingMessage],
    failingMessages: Seq[IncomingMessage],
    outputs: Seq[ComparisonType]
  ): Unit = {

    val downstream = new MemorySNSDownstream

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
    outputs: () => Seq[ComparisonType],
    downstreamDescription: String = "sends all the identifiers downstream"
  ): Unit = {
    val downstream = new MemorySNSDownstream
    whenReady(
      lambdaBuilder(downstream).processMessages(messages = messages)
    ) {
      response =>
        it("returns no results") {
          response shouldBe empty
        }
        it(downstreamDescription) {
          convertForComparison(
            getMessages(downstream)
          ) should contain theSameElementsAs outputs()
        }
    }
  }
}

trait LambdaBehavioursStringInStringOut[
  Config <: ApplicationConfig
] extends LambdaBehaviours[String, Config, String, String] {
  this: AnyFunSpec =>

  protected def convertForComparison: Seq[String] => Seq[String] =
    identity[Seq[String]]

  override protected def getMessages(
    downstream: MemorySNSDownstream
  ): Seq[String] =
    downstream.msgSender.messages.map(_.body)

}
