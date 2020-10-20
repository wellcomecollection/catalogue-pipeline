package uk.ac.wellcome.platform.merger

import scala.collection.mutable.Map
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec

import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.fixtures.{
  MatcherResultFixture,
  WorkerServiceFixture
}
import WorkState.Merged
import WorkFsm._

class MergerIntegrationTest
    extends AnyFunSpec
    with Eventually
    with IntegrationPatience
    with MatcherResultFixture
    with WorkerServiceFixture
    with WorkGenerators {

  it("reads matcher result messages off a queue and deletes them") {
    val workSender = new MemoryMessageSender()

    withVHS { vhs =>
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          val index = Map.empty[String, Work[Merged]]
          withWorkerService(vhs, queue, workSender, index = index) { _ =>
            val work = sourceWork()

            givenStoredInVhs(vhs, work)

            val matcherResult = matcherResultWith(Set(Set(work)))
            sendNotificationToSQS(queue, matcherResult)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
              getWorksSent(workSender) should contain only work.id
              index shouldBe Map(work.id -> work.transition[Merged]((None, 1))
            }
          }
      }
    }
  }
}
