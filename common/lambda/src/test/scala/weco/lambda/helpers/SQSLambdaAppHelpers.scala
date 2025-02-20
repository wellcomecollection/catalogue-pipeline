package weco.lambda.helpers

import com.typesafe.config.Config
import weco.lambda.{
  ApplicationConfig,
  SQSBatchResponseLambdaApp,
  SQSLambdaApp,
  SQSLambdaMessage,
  SQSLambdaMessageResult
}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.Future

trait SQSLambdaAppHelpers {
  // This value is from application.conf in test resources
  val expectedConfigString = "knownConfigValue"

  case class TestLambdaAppConfiguration(configString: String)
      extends ApplicationConfig

  class TestBatchResponseLambdaApp(
    messageResults: Seq[SQSLambdaMessageResult] = Seq.empty
  ) extends SQSBatchResponseLambdaApp[String, TestLambdaAppConfiguration] {
    override protected val maximumExecutionTime: FiniteDuration = 200.millis

    val messageResultsMap = messageResults.map {
      message =>
        message.messageId -> message
    }.toMap

    // Config is available in this scope
    val configString: String = config.configString

    // Function to convert typesafe config to application config is required
    override def build(rawConfig: Config): TestLambdaAppConfiguration =
      TestLambdaAppConfiguration(
        configString = rawConfig.getString("configString")
      )

    override def processMessages(
      messages: Seq[SQSLambdaMessage[String]]
    ): Future[Seq[SQSLambdaMessageResult]] = Future {
      messages.map {
        message =>
          messageResultsMap.getOrElse(
            message.messageId,
            throw new RuntimeException(
              s"Message ${message.messageId} not found in messageResults"
            )
          )
      }
    }
  }

  class SleepingTestBatchResponseLambdaApp extends TestBatchResponseLambdaApp {
    override def processMessages(
      messages: Seq[SQSLambdaMessage[String]]
    ): Future[Seq[SQSLambdaMessageResult]] = {
      Thread.sleep(500)
      super.processMessages(messages)
    }
  }

  class TestLambdaApp
      extends SQSLambdaApp[String, String, TestLambdaAppConfiguration] {
    override protected val maximumExecutionTime: FiniteDuration = 200.millis

    // Config is available in this scope
    val configString: String = config.configString

    // Function to convert typesafe config to application config is required
    override def build(rawConfig: Config): TestLambdaAppConfiguration =
      TestLambdaAppConfiguration(
        configString = rawConfig.getString("configString")
      )

    override def processT(t: List[String]): Future[String] =
      Future.successful(t.mkString + configString)
  }

  class FailingTestLambdaApp extends TestLambdaApp {
    override def processT(t: List[String]): Future[String] =
      Future.failed(new Throwable("Failed"))
  }

  class SleepingTestLambdaApp extends TestLambdaApp {
    override def processT(t: List[String]): Future[String] = Future {
      Thread.sleep(500)
      t.head + configString
    }
  }
}
