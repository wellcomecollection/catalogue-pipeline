package uk.ac.wellcome.platform.transformer.sierra.services

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.bigmessaging.message.MessageNotification
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.transformer.common.worker.Transformer
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.{StoreReadError, StoreWriteError, Version}
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Try}

class SierraTransformerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with IntegrationPatience
    with BigMessagingFixture
    with SierraGenerators
    with IdentifiersGenerators {

  it("transforms sierra records and publishes the result to the given topic") {
    withLocalSqsQueue { queue =>
      val id = createSierraBibNumber
      val title = "A pot of possums"
      val sierraTransformable = SierraTransformable(
        bibRecord = createSierraBibRecordWith(
          id = id,
          data = s"""
               |{
               | "id": "$id",
               | "title": "$title",
               | "varFields": []
               |}
                    """.stripMargin
        )
      )
      val key = Version(id.withoutCheckDigit, 0)
      val store =
        createStore[SierraTransformable](Map(key -> sierraTransformable))
      val sender = new MemoryBigMessageSender[TransformedBaseWork]()
      withWorkerService(store, sender, queue) { _ =>
        sendNotificationToSQS(queue, key)

        eventually {

          val sourceIdentifier =
            createSierraSystemSourceIdentifierWith(
              value = id.withCheckDigit
            )

          val sierraIdentifier =
            createSierraIdentifierSourceIdentifierWith(
              value = id.withoutCheckDigit
            )

          val works = sender.getMessages[UnidentifiedWork]
          works.length shouldBe >=(1)

          works.map { actualWork =>
            actualWork.sourceIdentifier shouldBe sourceIdentifier
            actualWork.data.title shouldBe Some(title)
            actualWork.identifiers shouldBe List(
              sourceIdentifier,
              sierraIdentifier)
          }
        }
      }
    }
  }

  it("receives a message and adds the version to the transformed work") {
    val version = 5
    val transformable = createSierraTransformable
    val key = Version(transformable.sierraId.withoutCheckDigit, version)
    val store = createStore(Map(key -> transformable))
    val sender = new MemoryBigMessageSender[TransformedBaseWork]()
    withLocalSqsQueue { queue =>
      sendNotificationToSQS(queue, key)

      withWorkerService(store, sender, queue) { _ =>
        eventually {
          val works = sender.getMessages[TransformedBaseWork]
          works.size should be >= 1

          works.map { actualWork =>
            actualWork shouldBe a[UnidentifiedWork]
            val unidentifiedWork = actualWork.asInstanceOf[UnidentifiedWork]
            unidentifiedWork.version shouldBe version
          }
        }
      }
    }
  }

  it("fails if store errors when retrieving the record") {
    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue, Version(randomAlphanumeric, 1))

        val store = brokenStore
        val sender = new MemoryBigMessageSender[TransformedBaseWork]()
        withWorkerService(store, sender, queue) { _ =>
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
            sender.getMessages[TransformedBaseWork].toList shouldBe Nil
          }
        }
    }
  }

  it("fails if the record does not exist in store") {

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        val store = createStore[SierraTransformable]()
        val sender = new MemoryBigMessageSender[TransformedBaseWork]()
        sendNotificationToSQS(queue, Version(randomAlphanumeric, 1))
        withWorkerService(store, sender, queue) { recordReceiver =>
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
            sender.getMessages[TransformedBaseWork].toList shouldBe Nil
          }
        }
    }
  }

  it("fails if it can't parse a key from SNS") {
    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        val store = createStore[SierraTransformable]()
        val sender = new MemoryBigMessageSender[TransformedBaseWork]()
        sendNotificationToSQS(
          queue,
          """{
          |"not a key": true
          |}""".stripMargin)
        withWorkerService(store, sender, queue) { recordReceiver =>
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
            sender.getMessages[TransformedBaseWork].toList shouldBe Nil
          }
        }
    }
  }

  it("fails if it's unable to perform a transformation") {
    val transformable = createSierraTransformable
    val key = Version(transformable.sierraId.withoutCheckDigit, 0)
    val store = createStore(Map(key -> transformable))
    val sender = new MemoryBigMessageSender[TransformedBaseWork]()
    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue, key)
        withBrokenWorkerService(store, sender, queue) { recordReceiver =>
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
            sender.getMessages[TransformedBaseWork].toList shouldBe Nil
          }
        }
    }
  }

  it("fails if it's unable to publish the work") {
    val transformable = createSierraTransformable
    val key = Version(transformable.sierraId.withoutCheckDigit, 0)
    val store = createStore(Map(key -> transformable))
    val sender = new MemoryBigMessageSender[TransformedBaseWork] {
      override def sendT(t: TransformedBaseWork): Try[MessageNotification] =
        Failure(new Exception("BOOM!"))
    }
    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue, key)
        withWorkerService(store, sender, queue) { recordReceiver =>
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
            sender.getMessages[TransformedBaseWork].toList shouldBe Nil
          }
        }
    }
  }

  def withWorkerService[R](
    store: VersionedStore[String, Int, SierraTransformable],
    sender: BigMessageSender[String, TransformedBaseWork],
    queue: Queue)(
    testWith: TestWith[SierraTransformerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new SierraTransformerWorkerService(
          stream = sqsStream,
          sender = sender,
          store = store
        )
        workerService.run()
        testWith(workerService)
      }
    }

  def withBrokenWorkerService[R](
    store: VersionedStore[String, Int, SierraTransformable],
    sender: BigMessageSender[String, TransformedBaseWork],
    queue: Queue)(
    testWith: TestWith[SierraTransformerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new SierraTransformerWorkerService(
          stream = sqsStream,
          sender = sender,
          store = store
        ) {
          override val transformer: Transformer[SierraTransformable] =
            (input: SierraTransformable, version: Int) =>
              Left(new Exception("AAAAAArgh!"))
        }
        workerService.run()
        testWith(workerService)
      }
    }

  private def brokenStore = {
    new MemoryVersionedStore[String, SierraTransformable](
      new MemoryStore(Map[Version[String, Int], SierraTransformable]())
      with MemoryMaxima[String, SierraTransformable]) {

      override def put(id: Version[String, Int])(
        entry: SierraTransformable): WriteEither =
        Left(StoreWriteError(new Error("BOOM!")))

      override def get(id: Version[String, Int]): ReadEither =
        Left(StoreReadError(new Error("BOOM!")))
    }
  }
  def createStore[T](
                      data: Map[Version[String, Int], T] = Map[Version[String, Int], T]())
  : MemoryVersionedStore[String, T] =
    new MemoryVersionedStore(new MemoryStore(data) with MemoryMaxima[String, T])
}
