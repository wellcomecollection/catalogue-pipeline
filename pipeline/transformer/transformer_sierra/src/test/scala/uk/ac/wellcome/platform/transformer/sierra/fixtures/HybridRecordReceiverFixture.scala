package uk.ac.wellcome.platform.transformer.sierra.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.HybridRecordReceiver
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.models.transformable.SierraTransformable

import uk.ac.wellcome.bigmessaging.fixtures.VHSFixture
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.sns.SNSConfig

import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket

trait HybridRecordReceiverFixture extends VHSFixture[SierraTransformable] {

  def withHybridRecordReceiver[R](
    vhs: VHS,
    topic: Topic,
    bucket: Bucket)(
    testWith: TestWith[HybridRecordReceiver[SNSConfig], R]): R =
    withSqsBigMessageSender[TransformedBaseWork, R](bucket, topic) { msgSender =>
      val recorderReciver = new HybridRecordReceiver(msgSender, vhs)
      testWith(recorderReciver)
  }
}
