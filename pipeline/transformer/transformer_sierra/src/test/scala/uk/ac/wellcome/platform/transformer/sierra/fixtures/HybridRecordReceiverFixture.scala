package uk.ac.wellcome.platform.transformer.sierra.fixtures

import com.amazonaws.services.sns.AmazonSNS
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.{Messaging, SNS}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.HybridRecordReceiver
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.Implicits._
//import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.ExecutionContext.Implicits.global

trait HybridRecordReceiverFixture extends Messaging with SNS {
  def withHybridRecordReceiver[R](
    topic: Topic,
    bucket: Bucket,
    snsClient: AmazonSNS = snsClient
  )(testWith: TestWith[HybridRecordReceiver, R])(
    implicit objectStore: ObjectStore[SierraTransformable]): R =
    withMessageWriter[TransformedBaseWork, R](
      bucket,
      topic,
      writerSnsClient = snsClient) { messageWriter =>
      val recordReceiver = new HybridRecordReceiver(
        messageWriter = messageWriter,
        objectStore = objectStore
      )

      testWith(recordReceiver)
    }
}
