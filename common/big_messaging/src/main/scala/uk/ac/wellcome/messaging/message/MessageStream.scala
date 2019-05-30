package uk.ac.wellcome.messaging.message

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.Message
import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSStream}
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.BigMessageReader

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MessageStream[T](sqsClient: AmazonSQSAsync,
                       sqsConfig: SQSConfig,
                       metricsSender: MetricsSender)(
  implicit
  actorSystem: ActorSystem,
  decoderT: Decoder[T],
  objectStoreT: ObjectStore[T],
  ec: ExecutionContext) {

  private val bigMessageReader = new BigMessageReader[T] {
    override val objectStore: ObjectStore[T] = objectStoreT
    override implicit val decoder: Decoder[T] = decoderT
  }

  private val sqsStream = new SQSStream[NotificationMessage](
    sqsClient = sqsClient,
    sqsConfig = sqsConfig,
    metricsSender = metricsSender
  )

  def runStream(
    streamName: String,
    modifySource: Source[(Message, T), NotUsed] => Source[Message, NotUsed])
    : Future[Done] =
    sqsStream.runStream(
      streamName,
      source => modifySource(messageFromS3Source(source)))

  def foreach(streamName: String, process: T => Future[Unit]): Future[Done] =
    sqsStream.foreach(
      streamName = streamName,
      process = (notification: NotificationMessage) =>
        for {
          body <- Future.fromTry {
            getBody(notification.body)
          }
          result <- process(body)
        } yield result
    )

  private def messageFromS3Source(
    source: Source[(Message, NotificationMessage), NotUsed])
    : Source[(Message, T), NotUsed] = {
    source.mapAsyncUnordered(sqsConfig.parallelism) {
      case (message, notification) =>
        for {
          deserialisedObject <- Future.fromTry {
            getBody(notification.body)
          }
        } yield (message, deserialisedObject)
    }
  }

  private def getBody(messageString: String): Try[T] =
    fromJson[MessageNotification](messageString).flatMap {
      bigMessageReader.read
    }
}
