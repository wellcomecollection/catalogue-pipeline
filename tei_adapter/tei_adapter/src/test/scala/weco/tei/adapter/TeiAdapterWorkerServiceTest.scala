package weco.tei.adapter;

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import weco.catalogue.tei.models.{TeiIdChangeMessage, TeiMetadata, TeiStoreRecord}

import java.time.ZonedDateTime
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream

class TeiAdapterWorkerServiceTest extends AnyFunSpec with SQS with Eventually with Akka with IntegrationPatience{
    it("processes a message from the tei id extractor"){
      withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
        withActorSystem { implicit ac =>
          implicit val ec = ac.dispatcher
          withSQSStream(queue) { stream: SQSStream[NotificationMessage] =>
            val messageSender = new MemoryMessageSender()
            val bucket = "bucket"
            val key = "key.xml"
            val message = TeiIdChangeMessage("manuscript_1234", S3ObjectLocation(bucket, key), ZonedDateTime.parse("2021-06-17T11:46:00+00:00"))

            sendNotificationToSQS(queue, message)
            val service = new TeiAdapterWorkerService(stream, messageSender)
            service.run()

            eventually {
              messageSender.getMessages[TeiStoreRecord] should contain only(TeiStoreRecord(message.id, TeiMetadata(false, message.s3Location, message.timeModified), 1))
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
        }
      }
    }
}
