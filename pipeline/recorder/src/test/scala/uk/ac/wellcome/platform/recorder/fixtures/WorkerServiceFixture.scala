package uk.ac.wellcome.platform.recorder.fixtures

import org.scalatest.Assertion
import uk.ac.wellcome.bigmessaging.fixtures.{BigMessagingFixture, VHSFixture}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.storage.store.memory.MemoryStore
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}

trait WorkerServiceFixture
    extends VHSFixture[TransformedBaseWork]
    with BigMessagingFixture {
  def withWorkerService[R](queue: Queue,
                           vhs: VHS,
                           messageSender: MemoryMessageSender =
                             new MemoryMessageSender())(
    testWith: TestWith[RecorderWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      implicit val store =
        new MemoryStore[ObjectLocation, TransformedBaseWork](Map.empty)
      withBigMessageStream[TransformedBaseWork, R](queue = queue) { msgStream =>
        val workerService =
          new RecorderWorkerService(vhs, msgStream, messageSender)
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
      Right(Identified(Version(id, expectedVhsVersion), work))
    Version(id, expectedVhsVersion)
  }

  def assertWorkNotStored[T <: TransformedBaseWork](vhs: VHS,
                                                    work: T): Assertion = {
    val id = work.sourceIdentifier.toString
    vhs.getLatest(id) shouldBe a[Left[_, _]]
  }
}
