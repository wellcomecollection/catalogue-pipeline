package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.must.Matchers
import weco.fixtures.RandomGenerators
import weco.lambda.helpers.{
  ConfigurationTestHelpers,
  SQSLambdaAppHelpers,
  SQSMessageHelpers
}
import scala.collection.JavaConverters._

class SQSBatchResponseLambdaAppTest
    extends AnyFunSpec
    with ConfigurationTestHelpers
    with SQSLambdaAppHelpers
    with SQSMessageHelpers
    with RandomGenerators
    with Matchers {

  it(
    "creates a lambda app with a config, and allows execution of a processMessages function"
  ) {
    val eventString = randomAlphanumeric()
    val fakeEvents = createSQSEvents(List(eventString))

    val messageId = fakeEvents.getRecords.get(0).getMessageId
    val messagesToProcess = SQSLambdaMessageProcessed(messageId)

    val lambdaApp = new TestBatchResponseLambdaApp(List(messagesToProcess))

    val expectedResult = new SQSBatchResponse()
    expectedResult.setBatchItemFailures(List().asJava)

    lambdaApp.handleRequest(fakeEvents, null) mustBe expectedResult
  }

  it(
    "creates a lambda app with a config, and allows execution of a processMessages function, handling multiple events"
  ) {
    // allow up to N events to be processed
    val eventStrings = (1 to 10).map {
      _ =>
        randomAlphanumeric()
    }.toList

    val fakeEvents = createSQSEvents(eventStrings)

    val messagesToProcess = fakeEvents.getRecords.asScala.map {
      message =>
        SQSLambdaMessageProcessed(message.getMessageId)
    }.toList

    val lambdaApp = new TestBatchResponseLambdaApp(messagesToProcess)

    val expectedResult = new SQSBatchResponse()
    expectedResult.setBatchItemFailures(List().asJava)

    lambdaApp.handleRequest(fakeEvents, null) mustBe expectedResult
  }

  it(
    "returns a batch response with the retryable failures only, if there is a mixed result state"
  ) {
    // include retryable failures, permanent failures, and successful messages
    val eventStrings = (1 to 9).map {
      _ =>
        randomAlphanumeric()
    }.toList

    val fakeEvents = createSQSEvents(eventStrings)

    val messagesToProcess =
      fakeEvents.getRecords.asScala.toList.zipWithIndex.map {
        case (message, i) =>
          if (i % 3 == 0) {
            SQSLambdaMessageFailedRetryable(
              message.getMessageId,
              new Throwable("Failed, but retryable")
            )
          } else if (i % 2 == 0) {
            SQSLambdaMessageFailedPermanent(
              message.getMessageId,
              new Throwable("Failed, and permanent")
            )
          } else {
            SQSLambdaMessageProcessed(message.getMessageId)
          }
      }

    val lambdaApp = new TestBatchResponseLambdaApp(messagesToProcess)

    val expectedResult = new SQSBatchResponse()

    val expectedFailures = messagesToProcess.collect {
      case failure: SQSLambdaMessageFailedRetryable =>
        new SQSBatchResponse.BatchItemFailure(failure.messageId)
    }.asJava

    expectedResult.setBatchItemFailures(expectedFailures)

    lambdaApp.handleRequest(fakeEvents, null) mustBe expectedResult
  }

  it("fails if the processEvent function takes too long") {
    val lambdaApp = new SleepingTestBatchResponseLambdaApp()
    val eventString = randomAlphanumeric()

    a[Throwable] shouldBe thrownBy {
      lambdaApp.handleRequest(createSQSEvents(List(eventString)), null)
    }
  }
}
