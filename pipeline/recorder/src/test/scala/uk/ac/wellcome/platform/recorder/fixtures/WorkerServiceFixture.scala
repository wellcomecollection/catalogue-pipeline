package uk.ac.wellcome.platform.recorder.fixtures

import scala.util.{Success, Try}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.{Encoder, Decoder}

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.bigmessaging.typesafe.{EmptyMetadata, GetLocation}

import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender

import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  StoreWriteError,
  Version
}
import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.streaming.Codec

trait WorkerServiceFixture extends BigMessagingFixture {

  type Entry = HybridStoreEntry[TransformedBaseWork, EmptyMetadata]

  type RecorderVhs = VersionedStore[String, Int, Entry] with GetLocation

  type InternalStore =
    MemoryStore[Version[String, Int], Entry] with MemoryMaxima[String, Entry]

  class MemoryRecorderVhs(
    internalStore: InternalStore =
      new MemoryStore[Version[String, Int], Entry](Map.empty)
      with MemoryMaxima[String, Entry]
  ) extends MemoryVersionedStore[String, Entry](internalStore)
      with GetLocation {

    def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
      Success(ObjectLocation("test", s"${key.id}/${key.version}"))
  }


  class BrokenMemoryRecorderVhs extends MemoryRecorderVhs() {
    override def put(id: Version[String, Int])(entry: Entry): WriteEither =
      Left(StoreWriteError(new Error("BOOM!")))
  }

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required)
  implicit val workEncoder: Encoder[TransformedBaseWork] = deriveEncoder
  implicit val workDecoder: Decoder[TransformedBaseWork] = deriveDecoder
  implicit val workCodec: Codec[TransformedBaseWork] = Codec.typeCodec

  def withRecorderVhs[R](testWith: TestWith[RecorderVhs, R]): R = {
    testWith(new MemoryRecorderVhs())
  }

  def withBrokenRecorderVhs[R](testWith: TestWith[RecorderVhs, R]): R = {
    testWith(new BrokenMemoryRecorderVhs())
  }

  def withMemoryMessageSender[R](
    testWith: TestWith[MemoryMessageSender, R]): R = {
    testWith(new MemoryMessageSender())
  }

  def withWorkerService[R](queue: Queue,
                           vhs: RecorderVhs,
                           msgSender: MemoryMessageSender)(
    testWith: TestWith[RecorderWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      implicit val streamStore =
        MemoryTypedStoreCompanion[ObjectLocation, TransformedBaseWork]()
      withMessageStream[TransformedBaseWork, R](queue = queue) { msgStream =>
        val workerService = new RecorderWorkerService(vhs, msgStream, msgSender)
        workerService.run()
        testWith(workerService)
      }
    }

  def assertWorkStored[T <: TransformedBaseWork](
    vhs: RecorderVhs,
    work: T,
    expectedVhsVersion: Int = 0): Version[String, Int] = {

    val id = work.sourceIdentifier.toString
    vhs.getLatest(id) shouldBe
      Right(
        Identified(
          Version(id, expectedVhsVersion),
          HybridStoreEntry(work, EmptyMetadata())))
    Version(id, expectedVhsVersion)
  }

  def assertWorkNotStored[T <: TransformedBaseWork](vhs: RecorderVhs,
                                                    work: T) = {

    val id = work.sourceIdentifier.toString
    val workExists = vhs.getLatest(id) match {
      case Left(_)  => false
      case Right(_) => true
    }
    workExists shouldBe false
  }
}
