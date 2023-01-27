package weco.pipeline.reindex_worker.services

import io.circe.Encoder
import weco.messaging.IndividualMessageSender

import scala.concurrent.{ExecutionContext, Future}

class BulkMessageSender[Destination](
  underlying: IndividualMessageSender[Destination]
)(implicit ec: ExecutionContext) {
  def send[T](messages: Seq[T], destination: Destination)(
    implicit encoder: Encoder[T]
  ): Future[Seq[Unit]] =
    Future.sequence {
      messages
        .map {
          m =>
            Future.fromTry {
              underlying.sendT(m)(
                subject = "Sent from the reindex_worker",
                destination = destination
              )
            }
        }
    }
}
