package uk.ac.wellcome.platform.recorder

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.LegacyWorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture
import uk.ac.wellcome.storage.Version
import WorkState.Source

class RecorderIntegrationTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with WorkerServiceFixture
    with LegacyWorkGenerators {

  it("saves received works to VHS, and puts the VHS key on the queue") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      val vhs = new MemoryVHS()

      withWorkerService(queue, vhs, messageSender) { _ =>
        val work = createSourceWork
        sendMessage[Work[Source]](queue = queue, obj = work)
        eventually {
          val key = assertWorkStored(vhs, work)

          messageSender.getMessages[Version[String, Int]] shouldBe Seq(key)
        }
      }
    }
  }
}
