package uk.ac.wellcome.platform.merger

import org.scalatest.FunSpec
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{BaseWork, TransformedBaseWork}
import uk.ac.wellcome.platform.merger.fixtures.{
  MatcherResultFixture,
  WorkerServiceFixture
}
import uk.ac.wellcome.storage.streaming.CodecInstances._

class MergerFeatureTest
    extends FunSpec
    with Messaging
    with IntegrationPatience
    with ScalaFutures
    with MatcherResultFixture
    with WorkerServiceFixture
    with WorksGenerators {

  it("reads matcher result messages off a queue and deletes them") {
    withLocalSnsTopic { topic =>
      val vhs = createVhs()

      val messageSender = new MemoryBigMessageSender[BaseWork]()

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(vhs, messageSender, queue) { _ =>
            val work = createUnidentifiedWork

            storeInVhs(vhs, work)

            val matcherResult = matcherResultWith(Set(Set(work)))
            sendNotificationToSQS(queue, matcherResult)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              val worksSent = messageSender.getMessages[TransformedBaseWork]
              worksSent should contain only work
            }
          }
      }
    }
  }
}
