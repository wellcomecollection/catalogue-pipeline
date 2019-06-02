package uk.ac.wellcome.platform.idminter.fixtures

import io.circe.Json
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.platform.idminter.database.MemoryIdentifiersDao
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
import uk.ac.wellcome.storage.streaming.CodecInstances._

trait WorkerServiceFixture extends Messaging {
  def withWorkerService[R](messageSender: MemoryBigMessageSender[Json],
                           queue: Queue,
                           identifiersDao: MemoryIdentifiersDao)(
    testWith: TestWith[IdMinterWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withMetricsSender() { metricsSender =>
        withMessageStream[Json, R](queue, metricsSender) { messageStream =>
          val workerService = new IdMinterWorkerService(
            idEmbedder = new IdEmbedder(
              identifierGenerator = new IdentifierGenerator(
                identifiersDao = identifiersDao
              )
            ),
            messageSender = messageSender,
            messageStream = messageStream
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }
}
