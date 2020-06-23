package uk.ac.wellcome.platform.recorder.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService

import uk.ac.wellcome.bigmessaging.fixtures.VHSFixture
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.MessageSender

import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}
import uk.ac.wellcome.storage.store.memory.MemoryStore

trait WorkerServiceFixture extends VHSFixture[TransformedBaseWork] {

  def withWorkerService[R, D](queue: Queue,
                              vhs: VHS,
                              msgSender: MessageSender[D])(
    testWith: TestWith[RecorderWorkerService[D], R]): R =
    withActorSystem { implicit actorSystem =>
      implicit val store =
        new MemoryStore[ObjectLocation, TransformedBaseWork](Map.empty)
      withBigMessageStream[TransformedBaseWork, R](queue = queue) { msgStream =>
        val workerService = new RecorderWorkerService(vhs, msgStream, msgSender)
        workerService.run()
        testWith(workerService)
      }
    }

  def assertWorkStored[T <: TransformedBaseWork](
    vhs: VHS,
    work: T,
    expectedVhsVersion: Int = 0): Version[String, Int] = {

    val id = work.sourceIdentifier.toString
    vhs.getLatest(id) shouldBe
      Right(
        Identified(
          Version(id, expectedVhsVersion),
          work))
    Version(id, expectedVhsVersion)
  }

  def assertWorkNotStored[T <: TransformedBaseWork](vhs: VHS, work: T) = {

    val id = work.sourceIdentifier.toString
    vhs.getLatest(id) shouldBe a[Left[_, _]]
  }
}
