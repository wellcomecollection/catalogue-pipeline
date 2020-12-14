package uk.ac.wellcome.platform.reindex.reindex_worker.services

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.IndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.models.source.ReindexPayload

import scala.concurrent.{ExecutionContext, Future}

class BulkMessageSender[Destination](
  underlying: IndividualMessageSender[Destination])(
  implicit ec: ExecutionContext) {
  def send(messages: Seq[ReindexPayload], destination: Destination): Future[Seq[Unit]] =
    Future.sequence {
      messages
        .map { m =>
          Future.fromTry {
            underlying.sendT(m)(
              subject = "Sent from the reindex_worker",
              destination = destination)
          }
        }
    }
}
