package uk.ac.wellcome.platform.recorder.fixtures

import scala.util.{Try, Success}

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.platform.recorder.{EmptyMetadata, GetLocation}

import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender

import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.{MemoryVersionedStore, MemoryStore}
import uk.ac.wellcome.storage.{Version, ObjectLocation}
import uk.ac.wellcome.storage.store.{VersionedStore, HybridStoreEntry}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima

trait WorkerServiceFixture extends BigMessagingFixture {

  type Entry = HybridStoreEntry[TransformedBaseWork, EmptyMetadata]

  type RecorderVhs = VersionedStore[String, Int, Entry] with GetLocation

  def withRecorderVhs[R](testWith: TestWith[RecorderVhs, R]): R = {
    val internalStore =
      new MemoryStore[Version[String, Int], Entry](Map.empty) with MemoryMaxima[String, Entry]
    val vhs =
      new MemoryVersionedStore[String, Entry](internalStore) with GetLocation {
        def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
          Success(ObjectLocation("test", s"${key.id}/${key.version}"))
      }
    testWith(vhs)
  }

  def withWorkerService[R](
    storageBucket: Bucket,
    topic: Topic,
    queue: Queue)(testWith: TestWith[(RecorderWorkerService[String], RecorderVhs), R]): R =
    withActorSystem { implicit actorSystem =>
      implicit val streamStore = MemoryTypedStoreCompanion[ObjectLocation, TransformedBaseWork]()
      withMessageStream[TransformedBaseWork, R](queue = queue) { msgStream =>
        withRecorderVhs[R] { vhs =>
          val msgSender = new MemoryMessageSender()
          val workerService = new RecorderWorkerService(vhs, msgStream, msgSender)
          workerService.run()
          testWith((workerService, vhs))
        }
      }
    }
}
