package weco.pipeline.router

import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.identifiers.IdentifierType.SierraSystemNumber
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.catalogue.internal_model.work.{CollectionPath, Relations, Work}
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.PipelineStorageStream._
import weco.pipeline_storage.{Indexable, PipelineStorageStream, Retriever}
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class RouterWorkerService[MsgDestination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Denormalised],
                                        MsgDestination],
  pathsMsgSender: MessageSender[MsgDestination],
  pathConcatenatorMsgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[Work[Merged]]
)(implicit ec: ExecutionContext, indexable: Indexable[Work[Denormalised]])
    extends Runnable {
  def run(): Future[Done] = {
    pipelineStream.run(
      this.getClass.getSimpleName,
      Flow[(Message, NotificationMessage)]
        .via(batchRetrieveFlow(pipelineStream.config, workRetriever))
        .via(
          processFlow(
            pipelineStream.config,
            work => Future.fromTry(processMessage(work))
          )
        )
    )
  }

  private def processMessage(
    work: Work[Merged]
  ): Try[List[Work[Denormalised]]] = {
    work.data.collectionPath match {
      case None =>
        Success(List(work.transition[Denormalised](Relations.none)))
      case Some(CollectionPath(path, _)) =>
val sender = work.sourceIdentifier.identifierType match {
  case SierraSystemNumber => pathConcatenatorMsgSender
  case _ => pathsMsgSender
}

sender.send(path).map(_ => Nil)

    }
  }

}
