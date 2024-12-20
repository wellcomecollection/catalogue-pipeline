package weco.lambda.helpers

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.Config
import weco.lambda.{ApplicationConfig, SQSLambdaApp}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

import scala.concurrent.Future
import collection.JavaConverters._

trait SQSLambdaAppHelpers {
  // This value is from application.conf in test resources
  val expectedConfigString = "knownConfigValue"

  case class TestLambdaAppConfiguration(configString: String)
    extends ApplicationConfig

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
    override def processT(t: List[String]): Future[String] = Future.failed(new Throwable("Failed"))
  }

  class SleepingTestLambdaApp extends TestLambdaApp {
    override def processT(t: List[String]): Future[String] = Future {
      Thread.sleep(500)
      t.head + configString
    }
  }

  def createSqsEvent(eventString: List[String]): SQSEvent = {
    val sqsEvent = new SQSEvent()
    sqsEvent.setRecords(eventString.map { record =>
      val sqsRecord = new SQSEvent.SQSMessage()
      sqsRecord.setBody(s"""{"Message": "$record"}""")
      sqsRecord
    }.asJava)
    sqsEvent
  }
}
