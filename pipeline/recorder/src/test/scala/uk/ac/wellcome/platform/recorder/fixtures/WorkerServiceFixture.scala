package uk.ac.wellcome.platform.recorder.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.storage.memory.{MemoryObjectStore, MemoryVersionedDao}
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture extends Messaging {
  type RecorderEntry = Entry[String, EmptyMetadata]

  type RecorderDao = MemoryVersionedDao[String, RecorderEntry]
  type RecorderStore = MemoryObjectStore[TransformedBaseWork]
  type RecorderVhs = VersionedHybridStore[String, TransformedBaseWork, EmptyMetadata]

  def createDao: RecorderDao =
    MemoryVersionedDao[String, RecorderEntry]()

  def createStore: RecorderStore = new RecorderStore()

  def createVhs(dao: RecorderDao, store: RecorderStore): RecorderVhs =
    new RecorderVhs {
      override protected val versionedDao: RecorderDao = dao
      override protected val objectStore: RecorderStore = store
    }

  def withWorkerService[R](
    dao: RecorderDao,
    store: RecorderStore,
    messageSender: MemoryMessageSender,
    queue: Queue)(testWith: TestWith[RecorderWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      val vhs = createVhs(dao, store)

      withMessageStream[TransformedBaseWork, R](queue) { messageStream =>
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
