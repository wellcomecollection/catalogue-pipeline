package uk.ac.wellcome.platform.merger

import org.scalatest.FunSpec
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.merger.fixtures.{
  MatcherResultFixture,
  WorkerServiceFixture
}
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair

class MergerIntegrationTest
    extends AnyFunSpec
    with BigMessagingFixture
    with IntegrationPatience
    with ScalaFutures
    with MatcherResultFixture
    with WorkerServiceFixture
    with WorksGenerators {

  it("reads matcher result messages off a queue and deletes them") {
    withLocalSnsTopic { worksTopic =>
      withLocalSnsTopic { imagesTopic =>
        withVHS { vhs =>
          withLocalSqsQueueAndDlq {
            case QueuePair(queue, dlq) =>
              withWorkerService(vhs, queue, worksTopic, imagesTopic) { _ =>
                val work = createUnidentifiedWork

                givenStoredInVhs(vhs, work)

                val matcherResult = matcherResultWith(Set(Set(work)))
                sendNotificationToSQS(queue, matcherResult)

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueEmpty(dlq)
                  val worksSent = getMessages[TransformedBaseWork](worksTopic)
                  worksSent should contain only work
                }
              }
          }
        }
      }
    }
  }
}
