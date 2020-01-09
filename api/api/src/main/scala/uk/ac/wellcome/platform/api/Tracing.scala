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

// Some context on why there's a custom dispatcher here, and what `currentTransaction` is all about:

// APM stores activated transactions and spans in a ThreadLocal pool, which is why it requires that Scopes are
// closed on the same thread on which they are started. Scala's Futures are run in a ForkJoinPool to which new
// Futures are enqueued: they can either execute on the thread from which they were created, a new thread that
// is forked by the pool, or be 'stolen' by an existing thread that has become available.
//
// mapping on Futures creates new ones which, at low volume, are likely to be run on the same thread thus
// preserving the integrity of APM's ThreadLocals. However, at high request volume, the forking and work-stealing
// is much more likely to occur (due to the asynchronicity of requests), and so we cannot guarantee that the mapping
// function is run in the same thread as the Future which it maps. This results in closing Scopes on new threads,
// and referring to "current" transactions which are either null-transactions or are unrelated, causing the
// problems described above.

object Tracing {
  def init(config: Config)(implicit actorSystem: ActorSystem): Unit = {
    ElasticApmAttacher.attach(
      Map(
        "application_packages" -> "uk.ac.wellcome",
        "service_name" -> config.getOrElse("apm.service.name")("catalogue-api"),
        "server_urls" -> config.getOrElse("apm.server.url")(
          "http://localhost:9200"),
        "secret_token" -> config.getOrElse[String]("apm.secret")(""),
        "span_frames_min_duration" -> "250ms"
      ).asJava)
    actorSystem.dispatchers.registerConfigurator(
      "tracing-dispatcher",
      new TraceableDispatcherConfigurator(
        actorSystem.dispatchers.defaultDispatcherConfig,
        actorSystem.dispatchers.prerequisites))
    ec = actorSystem.dispatchers.lookup("tracing-dispatcher")
  }

  // This is set to the global EC so that tests work.
  // In practice it's mutated by `init` but this does mean that if
  // you forget to call `init` then you might experience issues.
  implicit var ec: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  // This is copied across from the current thread when executing a new Runnable (ie a Future) by the dispatcher
  // configured in TraceableDispatcherConfigurator across. The WeakReference ensures that APM controls the memory
  // in which the Transaction lives (we never truly acquire the object) and so prevents the possibility of memory leaks.
  private val threadLocalTransactionReference =
    new DynamicVariable[WeakReference[Transaction]](
      new WeakReference[Transaction](ElasticApm.currentTransaction())
    )

  def currentTransaction: Transaction =
    threadLocalTransactionReference.value.get
      .getOrElse(ElasticApm.currentTransaction())

  def currentTransaction_=(t: Transaction) {
    threadLocalTransactionReference.value = new WeakReference[Transaction](t)
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

  // This is to activate the currentTransaction for cases where we want APM's auto-instrumentation to kick in:
  // for us, that's for it to instrument the calls to the Elastic REST API.
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

// This ensures that the currentTransaction is always valid: see comments on `currentTransaction` above.
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
