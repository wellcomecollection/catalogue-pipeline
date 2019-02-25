package uk.ac.wellcome.platform.transformer.sierra.services

import akka.Done
import uk.ac.wellcome.messaging.sqs.NotificationStream
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformableTransformer
import uk.ac.wellcome.storage.vhs.HybridRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class SierraTransformerWorkerService(
  notificationStream: NotificationStream[HybridRecord],
  messageReceiver: HybridRecordReceiver[SierraTransformable],
  sierraTransformer: SierraTransformableTransformer
) extends Runnable {

  def run(): Future[Done] =
    notificationStream.run {
      hybridRecord: HybridRecord =>
        messageReceiver.receiveMessage(hybridRecord, sierraTransformer.transform)
    }
}
