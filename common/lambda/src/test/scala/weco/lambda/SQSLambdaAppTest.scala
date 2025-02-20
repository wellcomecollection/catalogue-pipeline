package weco.lambda

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.must.Matchers
import weco.fixtures.RandomGenerators
import weco.lambda.helpers.{
  ConfigurationTestHelpers,
  SQSLambdaAppHelpers,
  SQSMessageHelpers
}

class SQSLambdaAppTest
    extends AnyFunSpec
    with ConfigurationTestHelpers
    with SQSLambdaAppHelpers
    with SQSMessageHelpers
    with RandomGenerators
    with Matchers {

  it(
    "creates a lambda app with a config, and allows execution of a processEvent function"
  ) {
    val lambdaApp = new TestLambdaApp()
    val eventString = randomAlphanumeric()

    lambdaApp.handleRequest(
      createSQSEvents(List(eventString)),
      null
    ) mustBe eventString + expectedConfigString
  }

  it(
    "creates a lambda app with a config, and allows execution of a processEvent function, handling multiple events"
  ) {
    val lambdaApp = new TestLambdaApp()
    val eventString1 = randomAlphanumeric()
    val eventString2 = randomAlphanumeric()

    lambdaApp.handleRequest(
      createSQSEvents(List(eventString1, eventString2)),
      null
    ) mustBe eventString1 + eventString2 + expectedConfigString
  }

  it("fails if the processEvent function fails") {
    val lambdaApp = new FailingTestLambdaApp()
    val eventString = randomAlphanumeric()

    a[Throwable] shouldBe thrownBy {
      lambdaApp.handleRequest(createSQSEvents(List(eventString)), null)
    }
  }

  it("fails if the processEvent function takes too long") {
    val lambdaApp = new SleepingTestLambdaApp()
    val eventString = randomAlphanumeric()

    a[Throwable] shouldBe thrownBy {
      lambdaApp.handleRequest(createSQSEvents(List(eventString)), null)
    }
  }
}
