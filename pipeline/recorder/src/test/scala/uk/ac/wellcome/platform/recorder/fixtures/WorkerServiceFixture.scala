package uk.ac.wellcome.platform.recorder.fixtures

import org.scalatest.Assertion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.message.{MessageNotification, RemoteNotification}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.storage.fixtures.S3
import uk.ac.wellcome.storage.memory.{MemoryObjectStore, MemoryVersionedDao}
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}

trait WorkerServiceFixture extends Messaging with S3 {
  type RecorderDao = MemoryVersionedDao[String, Entry[String, EmptyMetadata]]
  type RecorderStore = MemoryObjectStore[TransformedBaseWork]
  type RecorderVhs = VersionedHybridStore[String, TransformedBaseWork, EmptyMetadata]

  def createDao: RecorderDao = MemoryVersionedDao[String, Entry[String, EmptyMetadata]]()
  def createStore: RecorderStore = new RecorderStore()
  def createVhs(dao: RecorderDao = createDao, store: RecorderStore = createStore): RecorderVhs = new RecorderVhs {
    override protected val versionedDao: RecorderDao = dao
    override protected val objectStore: RecorderStore = store
  }

  def withWorkerService[R](
    vhs: VersionedHybridStore[String, TransformedBaseWork, EmptyMetadata],
    messageSender: MemoryMessageSender,
    queue: Queue)(testWith: TestWith[RecorderWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withMetricsSender() { metricsSender =>
        withMessageStream[TransformedBaseWork, R](
          queue = queue,
          metricsSender = metricsSender) { messageStream =>
          val workerService = new RecorderWorkerService(
            versionedHybridStore = vhs,
            messageStream = messageStream,
            messageSender = messageSender
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }

  def assertStoredSingleWork(
    messageSender: MemoryMessageSender,
    dao: RecorderDao,
    store: RecorderStore,
    expectedWork: TransformedBaseWork,
    expectedVersion: Int = 1): Assertion = {
    dao.entries should have size 1
    val entry = dao.entries(expectedWork.sourceIdentifier.toString)

    entry.version shouldBe expectedVersion

    val storedStream = store.storageBackend.get(entry.location).right.value
    val storedWork = typeCodec[TransformedBaseWork].fromStream(storedStream).right.value
    storedWork shouldBe expectedWork

    messageSender.getMessages[MessageNotification] should contain(RemoteNotification(entry.location))
  }
}
