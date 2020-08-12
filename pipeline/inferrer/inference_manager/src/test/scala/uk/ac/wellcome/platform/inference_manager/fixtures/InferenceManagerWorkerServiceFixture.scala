package uk.ac.wellcome.platform.inference_manager.fixtures

import akka.http.scaladsl.Http
import io.circe.{Decoder, Encoder}
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.inference_manager.services.{
  InferenceManagerWorkerService,
  InferrerAdapter
}

import scala.concurrent.ExecutionContext.Implicits.global

trait InferenceManagerWorkerServiceFixture[Input, Output]
    extends BigMessagingFixture
    with Akka {
  def withWorkerService[R](queue: Queue,
                           messageSender: MemoryMessageSender,
                           adapter: InferrerAdapter[Input, Output],
                           inferrerPort: Int)(
    testWith: TestWith[InferenceManagerWorkerService[String, Input, Output],
                       R])(implicit decoder: Decoder[Input],
                           encoder: Encoder[Output]): R =
    withActorSystem { implicit actorSystem =>
      withBigMessageStream[Input, R](queue) { msgStream =>
        val workerService = new InferenceManagerWorkerService(
          msgStream = msgStream,
          messageSender = messageSender,
          inferrerAdapter = adapter,
          inferrerClientFlow = Http()
            .cachedHostConnectionPool[(Message, Input)](
              "localhost",
              inferrerPort)
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
