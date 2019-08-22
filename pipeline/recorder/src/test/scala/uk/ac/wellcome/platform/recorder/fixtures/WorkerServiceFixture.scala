package uk.ac.wellcome.platform.recorder.fixtures

import scala.util.{Try, Success}

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.platform.recorder.{EmptyMetadata, GetLocation}

import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender

import uk.ac.wellcome.storage.store.memory.{MemoryVersionedStore, MemoryStore}
import uk.ac.wellcome.storage.{Version, ObjectLocation, StoreWriteError}
import uk.ac.wellcome.storage.store.{VersionedStore, HybridStoreEntry}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima

trait WorkerServiceFixture extends BigMessagingFixture {

  type Entry = HybridStoreEntry[TransformedBaseWork, EmptyMetadata]

  type RecorderVhs = VersionedStore[String, Int, Entry] with GetLocation

  type InternalStore = MemoryStore[Version[String, Int], Entry] with MemoryMaxima[String, Entry]

  class MemoryRecorderVhs(
    internalStore: InternalStore =
      new MemoryStore[Version[String, Int], Entry](Map.empty)
        with MemoryMaxima[String, Entry]
  ) extends MemoryVersionedStore[String, Entry](internalStore) with GetLocation {

    def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
      Success(ObjectLocation("test", s"${key.id}/${key.version}"))
  }

  class BrokenMemoryRecorderVhs extends MemoryRecorderVhs() {
    override def put(id: Version[String, Int])(entry: Entry): WriteEither =
      Left(StoreWriteError(new Error("BOOM!")))
  }

  def withRecorderVhs[R](testWith: TestWith[RecorderVhs, R]): R = {
    testWith(new MemoryRecorderVhs())
  }

  def withBrokenRecorderVhs[R](testWith: TestWith[RecorderVhs, R]): R = {
    testWith(new BrokenMemoryRecorderVhs())
  }

  def withMemoryMessageSender[R](testWith: TestWith[MemoryMessageSender, R]): R = {
    testWith(new MemoryMessageSender())
  }

  def withWorkerService[R](queue: Queue, vhs: RecorderVhs, msgSender: MemoryMessageSender)(
    testWith: TestWith[RecorderWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      implicit val streamStore = MemoryTypedStoreCompanion[ObjectLocation, TransformedBaseWork]()
      withMessageStream[TransformedBaseWork, R](queue = queue) { msgStream =>
        val workerService = new RecorderWorkerService(vhs, msgStream, msgSender)
        workerService.run()
        testWith(workerService)
      }
    }
}
