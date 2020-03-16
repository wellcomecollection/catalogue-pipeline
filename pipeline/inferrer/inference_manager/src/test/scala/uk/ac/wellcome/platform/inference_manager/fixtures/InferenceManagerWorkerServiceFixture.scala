package uk.ac.wellcome.platform.inference_manager.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.Http
import com.amazonaws.services.sqs.model.Message
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.platform.inference_manager.services.{
  InferenceManagerWorkerService,
  InferrerAdapter
}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import uk.ac.wellcome.storage.streaming.Codec

trait InferenceManagerWorkerServiceFixture[Input, Output]
    extends BigMessagingFixture {
  def withWorkerService[R](queue: Queue,
                           topic: Topic,
                           adapter: InferrerAdapter[Input, Output],
                           inferrerPort: Int)(
    testWith: TestWith[InferenceManagerWorkerService[SNSConfig, Input, Output],
                       R])(implicit decoder: Decoder[Input],
                           encoder: Encoder[Output],
                           codecOut: Codec[Output],
                           codecIn: Codec[Input]): R =
    withLocalS3Bucket { bucket =>
      withSqsBigMessageSender[Output, R](bucket, topic) { msgSender =>
        withActorSystem { implicit actorSystem =>
          withMaterializer(actorSystem) { implicit materializer =>
            implicit val typedStoreT: MemoryTypedStore[ObjectLocation, Input] =
              MemoryTypedStoreCompanion[ObjectLocation, Input]()
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
