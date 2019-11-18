package uk.ac.wellcome.platform.transformer.mets.service

import org.scalatest.FunSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal.{License_CCBYNC, TransformedBaseWork, UnidentifiedInvisibleWork}
import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket

class MetsTransformerWorkerServiceTest extends FunSpec with BigMessagingFixture with MetsGenerators with RandomStrings with Eventually with IntegrationPatience{

  it("retrieves a mets file from s3 and sends an invisible work"){
    withLocalSqsQueue{ queue =>
      withLocalSnsTopic { topic =>
        withLocalS3Bucket { messagingBucket =>
          withLocalS3Bucket { storageBucket =>
            val identifier = randomAlphanumeric(10)
            val version = randomInt(1, 10)
            s3Client.putObject(storageBucket.name,"mets.xml",metsXmlWith(identifier, License_CCBYNC))

            sendSqsMessage(queue, MetsData("mets.xml", version))

            withWorkerService(topic, messagingBucket, queue) { _ =>
              eventually{
                getMessages[UnidentifiedInvisibleWork](topic) should have size 1
              }
            }
          }
        }
      }
    }
  }

  def withWorkerService[R](topic: Topic,
                           bucket: Bucket,
                           queue: Queue)(
                            testWith: TestWith[MetsTransformerWorkerService, R]): R =
      withActorSystem { implicit actorSystem =>
        withSQSStream[MetsData, R](queue) { sqsStream =>
          withSqsBigMessageSender[TransformedBaseWork, R](bucket, topic, snsClient) { messageSender =>
            val workerService = new MetsTransformerWorkerService(sqsStream, messageSender)
            workerService.run()
            testWith(workerService)
          }
        }
      }
}
