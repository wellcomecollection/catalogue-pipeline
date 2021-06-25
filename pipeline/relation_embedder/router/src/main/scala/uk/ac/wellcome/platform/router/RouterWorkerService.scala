package uk.ac.wellcome.platform.router

import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import weco.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream._
import uk.ac.wellcome.pipeline_storage.{
  Indexable,
  PipelineStorageStream,
  Retriever
}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.internal_model.work.{Relations, Work}

import scala.concurrent.{ExecutionContext, Future}

class RouterWorkerService[MsgDestination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Denormalised],
                                        MsgDestination],
  pathsMsgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[Work[Merged]],
)(implicit ec: ExecutionContext, indexable: Indexable[Work[Denormalised]])
    extends Runnable {
  def run(): Future[Done] = {
    pipelineStream.run(
      this.getClass.getSimpleName,
      Flow[(Message, NotificationMessage)]
        .via(batchRetrieveFlow(pipelineStream.config, workRetriever))
        .via(processFlow(pipelineStream.config, item => processMessage(item)))
    )
  }

  private def processMessage(
    work: Work[Merged]): Future[List[Work[Denormalised]]] = {
    work.data.collectionPath
      .fold[Future[List[Work[Denormalised]]]](ifEmpty = {
        Future.successful(
          List(work.transition[Denormalised]((Relations.none, Set.empty))))
      }) { path =>
        Future.fromTry(pathsMsgSender.send(path.path)).map(_ => Nil)
      }
  }

}
