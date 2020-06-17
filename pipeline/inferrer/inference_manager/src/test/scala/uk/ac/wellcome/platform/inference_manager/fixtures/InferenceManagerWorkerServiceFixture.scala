package uk.ac.wellcome.platform.inference_manager.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.Http
import software.amazon.awssdk.services.sqs.model.Message
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.platform.inference_manager.services.{
  InferenceManagerWorkerService,
  InferrerAdapter
}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryStore

trait InferenceManagerWorkerServiceFixture[Input, Output]
    extends BigMessagingFixture {
  def withWorkerService[R](queue: Queue,
                           topic: Topic,
                           adapter: InferrerAdapter[Input, Output],
                           inferrerPort: Int)(
    testWith: TestWith[InferenceManagerWorkerService[SNSConfig, Input, Output],
                       R])(implicit decoder: Decoder[Input],
                           encoder: Encoder[Output]): R =
    withLocalS3Bucket { bucket =>
      withSqsBigMessageSender[Output, R](
        bucket,
        topic,
        bigMessageThreshold = Int.MaxValue) { msgSender =>
        withActorSystem { implicit actorSystem =>
          withMaterializer(actorSystem) { implicit materializer =>
            implicit val store: MemoryStore[ObjectLocation, Input] =
              new MemoryStore[ObjectLocation, Input](Map.empty)
            withBigMessageStream[Input, R](queue) { msgStream =>
              val workerService = new InferenceManagerWorkerService(
                msgStream = msgStream,
                msgSender = msgSender,
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
      }
    }
}
