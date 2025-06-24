package weco.lambda.behaviours

import io.circe.Decoder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.lambda.helpers.DownstreamHelper
import weco.lambda._
import weco.lambda.matchers.LambdaResultMatchers

trait LambdaBehaviours[T, Config <: ApplicationConfig]
    extends Matchers
    with LambdaResultMatchers[T]
    with DownstreamHelper
    with ScalaFutures {
  this: AnyFunSpec =>
  protected type LambdaApp = SQSBatchResponseLambdaApp[T, Config]
  protected type IncomingMessage = SQSLambdaMessage[T]
  protected type OutputMessageType
  protected implicit val outputDecoder: Decoder[OutputMessageType]

  def failingLambda(
    sut: Downstream => LambdaApp,
    messages: Seq[IncomingMessage]
  ): Unit = describe("When all messages fail") {
    val downstream = new MemoryDownstream
    whenReady(sut(downstream).processMessages(messages = messages)) {
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
}
