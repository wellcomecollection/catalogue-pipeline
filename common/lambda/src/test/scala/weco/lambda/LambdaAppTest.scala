package weco.lambda

import com.typesafe.config.Config
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.must.Matchers
import weco.fixtures.RandomGenerators
import weco.lambda.helpers.ConfigurationTestHelpers

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class LambdaAppTest
    extends AnyFunSpec
    with ConfigurationTestHelpers
    with RandomGenerators
    with Matchers {

  // This value is from application.conf in test resources
  val configString = "knownConfigValue"

  case class TestLambdaAppConfiguration(configString: String)
      extends ApplicationConfig
  class TestLambdaApp
      extends LambdaApp[String, String, TestLambdaAppConfiguration] {
    override protected val maximumExecutionTime: FiniteDuration = 200.millis

    // Config is available in this scope
    lazy val configString: String = config.configString

    // Function to process an event is required, and should return a Future
    override def processEvent(event: String): Future[String] =
      Future.successful(event + configString)

    // Function to convert typesafe config to application config is required
    override def build(rawConfig: Config): TestLambdaAppConfiguration =
      TestLambdaAppConfiguration(
        configString = rawConfig.getString("configString")
      )
  }

  it(
    "creates a lambda app with a config, and allows execution of a processEvent function"
  ) {
    val lambdaApp = new TestLambdaApp()
    val eventString = randomAlphanumeric()

    lambdaApp.handleRequest(
      eventString,
      null
    ) mustBe eventString + configString
  }

  class FailingTestLambdaApp extends TestLambdaApp {
    override def processEvent(event: String): Future[String] =
      Future.failed(new Throwable("Failed"))
  }

  it("fails if the processEvent function fails") {
    val lambdaApp = new FailingTestLambdaApp()
    val eventString = randomAlphanumeric()

    a[Throwable] shouldBe thrownBy {
      lambdaApp.handleRequest(eventString, null)
    }
  }

  class SleepingTestLambdaApp extends TestLambdaApp {
    override def processEvent(event: String): Future[String] = Future {
      Thread.sleep(500)
      event + configString
    }
  }

  it("fails if the processEvent function takes too long") {
    val lambdaApp = new SleepingTestLambdaApp()
    val eventString = randomAlphanumeric()

    a[Throwable] shouldBe thrownBy {
      lambdaApp.handleRequest(eventString, null)
    }
  }
}
