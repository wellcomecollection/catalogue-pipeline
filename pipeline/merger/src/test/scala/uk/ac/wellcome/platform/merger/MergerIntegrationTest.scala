package uk.ac.wellcome.platform.merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.fixtures.{
  MatcherResultFixture,
  WorkerServiceFixture
}
import WorkState.Unidentified

class MergerIntegrationTest
    extends AnyFunSpec
    with Eventually
    with IntegrationPatience
    with MatcherResultFixture
    with WorkerServiceFixture
    with WorksGenerators {

  it("reads matcher result messages off a queue and deletes them") {
    val workSender = new MemoryMessageSender()

    withVHS { vhs =>
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(vhs, queue, workSender) { _ =>
            val work = createUnidentifiedWork

            givenStoredInVhs(vhs, work)

            val matcherResult = matcherResultWith(Set(Set(work)))
            sendNotificationToSQS(queue, matcherResult)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              workSender
                .getMessages[Work[Unidentified]] should contain only work
            }
          }
      }
    }
  }
}
