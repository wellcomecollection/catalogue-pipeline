package uk.ac.wellcome.platform.api

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.dispatch.{
  Dispatcher,
  DispatcherPrerequisites,
  MessageDispatcher,
  MessageDispatcherConfigurator
}
import co.elastic.apm.api.{ElasticApm, Transaction}
import co.elastic.apm.attach.ElasticApmAttacher
import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.ref.WeakReference
import scala.util.{DynamicVariable, Failure, Success}

object Tracing {
  def init(config: Config)(implicit actorSystem: ActorSystem): Unit = {
    ElasticApmAttacher.attach(
      Map(
        "application_packages" -> "uk.ac.wellcome",
        "service_name" -> config.getOrElse("apm.service.name")("catalogue-api"),
        "server_urls" -> config.getOrElse("apm.server.url")(
          "http://localhost:9200"),
        "secret_token" -> config.getOrElse[String]("apm.secret")("")
      ).asJava)
    actorSystem.dispatchers.registerConfigurator(
      "tracing-dispatcher",
      new TraceableDispatcherConfigurator(
        actorSystem.dispatchers.defaultDispatcherConfig,
        actorSystem.dispatchers.prerequisites))
    ec = actorSystem.dispatchers.lookup("tracing-dispatcher")
  }

  implicit var ec: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  private val _currentTransaction =
    new DynamicVariable[WeakReference[Transaction]](
      new WeakReference[Transaction](ElasticApm.currentTransaction())
    )

  def currentTransaction: Transaction =
    _currentTransaction.value.get.getOrElse(ElasticApm.currentTransaction())

  def currentTransaction_=(t: Transaction) {
    _currentTransaction.value = new WeakReference[Transaction](t)
  }
}

trait Tracing {
  import Tracing.ec

  def spanFuture[T](
    name: String,
    spanType: String = "",
    subType: String = "",
    action: String = "")(wrappedFunction: => Future[T]): Future[T] = {
    val span = Tracing.currentTransaction
      .startSpan(spanType, subType, action)
      .setName(name)

    wrappedFunction.transform { res =>
      res match {
        case Success(_)      =>
        case Failure(reason) => span.captureException(reason)
      }
      span.end()
      res
    }
  }

  def transactFuture[T](name: String,
                        transactionType: String = Transaction.TYPE_REQUEST)(
    wrappedFunction: => Future[T]): Future[T] = {
    val transaction = ElasticApm
      .startTransaction()
      .setName(name)
      .setType(transactionType)
    Tracing.currentTransaction = transaction

    wrappedFunction
      .transform { res =>
        res match {
          case Success(_) =>
          case Failure(reason) =>
            transaction.captureException(reason)
        }
        transaction.end()
        res
      }
  }

  def withActiveTrace[T](wrapped: => T): T = {
    val scope = Tracing.currentTransaction.activate()
    val result = wrapped
    scope.close()
    result
  }
}

object Helpers {
  implicit class ConfigHelper(val config: Config) extends AnyVal {
    def getMillisDuration(path: String): FiniteDuration =
      getDuration(path, TimeUnit.MILLISECONDS)

    def getNanosDuration(path: String): FiniteDuration =
      getDuration(path, TimeUnit.NANOSECONDS)

    private def getDuration(path: String, unit: TimeUnit): FiniteDuration =
      Duration(config.getDuration(path, unit), unit)
  }
}

class TraceableDispatcherConfigurator(config: Config,
                                      prerequisites: DispatcherPrerequisites)
    extends MessageDispatcherConfigurator(config, prerequisites) {

  import Helpers._

  override def dispatcher(): MessageDispatcher =
    new Dispatcher(
      this,
      config.getString("id"),
      config.getInt("throughput"),
      config.getNanosDuration("throughput-deadline-time"),
      configureExecutor(),
      config.getMillisDuration("shutdown-timeout")
    ) { dispatcher =>
      override def execute(runnable: Runnable): Unit = {
        val transaction = Tracing.currentTransaction
        super.execute(() => {
          Tracing.currentTransaction = transaction
          runnable.run()
        })
      }
    }
}
