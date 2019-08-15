package uk.ac.wellcome.platform.recorder.fixtures

import org.scalatest.Assertion
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.bigmessaging.message.RemoteNotification
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.EmptyMetadata
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.memory.{
  MemoryHybridStore,
  MemoryStore,
  MemoryTypedStore
}
import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStore,
  HybridStoreEntry
}
import uk.ac.wellcome.storage.streaming.Codec._

trait WorkerServiceFixture extends BigMessagingFixture {

  def withMemoryHybridStore[R](
    testWith: TestWith[
      MemoryHybridStore[ObjectLocation, TransformedBaseWork, EmptyMetadata],
      R]): R = {

    type RecorderEntry = HybridIndexedStoreEntry[String, EmptyMetadata]
    type RecorderVhs =
      HybridStore[ObjectLocation, Int, TransformedBaseWork, EmptyMetadata]

    implicit val typedStore: MemoryTypedStore[String, TransformedBaseWork] =
      MemoryTypedStoreCompanion[String, TransformedBaseWork]()

    implicit val indexedStore: MemoryStore[ObjectLocation, RecorderEntry] =
      new MemoryStore[ObjectLocation, RecorderEntry](Map.empty)

    val vhs =
      new MemoryHybridStore[ObjectLocation, TransformedBaseWork, EmptyMetadata]

    testWith(vhs)
  }

  def withWorkerService[R](
    vhs: MemoryHybridStore[ObjectLocation, TransformedBaseWork, EmptyMetadata],
    messageSender: MemoryMessageSender,
    queue: Queue)(testWith: TestWith[RecorderWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      implicit val typedStoreT
        : MemoryTypedStore[ObjectLocation, TransformedBaseWork] =
        MemoryTypedStoreCompanion[ObjectLocation, TransformedBaseWork]()

      withMessageStream[TransformedBaseWork, R](queue) { messageStream =>
        val workerService = new RecorderWorkerService(
          vhs = vhs,
          messageStream = messageStream,
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def assertStoredSingleWork(vhs: HybridStore[ObjectLocation,
                                              ObjectLocation,
                                              TransformedBaseWork,
                                              EmptyMetadata],
                             messageSender: MemoryMessageSender,
                             expectedWork: TransformedBaseWork,
                             expectedVhsVersion: Int = 1): Assertion = {
    val actualNotifications = messageSender.getMessages[RemoteNotification]

    actualNotifications should have size 1
    vhs
      .get(actualNotifications.head.location)
      .right
      .value shouldBe expectedWork
  }
}
