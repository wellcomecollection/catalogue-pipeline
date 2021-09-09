package weco.pipeline.router

import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import weco.json.JsonUtil._
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.pipeline_storage.PipelineStorageStream._
import weco.typesafe.Runnable
import weco.catalogue.internal_model.work.{CollectionPath, Relations, Work}
import weco.pipeline_storage.{Indexable, PipelineStorageStream, Retriever}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class RouterWorkerService[MsgDestination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Denormalised],
                                        MsgDestination],
  pathsMsgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[Work[Merged]],
)(implicit ec: ExecutionContext, indexable: Indexable[Work[Denormalised]])
    extends Runnable {
  def run(): Future[Done] =
    pipelineStream.run(
      this.getClass.getSimpleName,
      Flow[(Message, NotificationMessage)]
        .via(batchRetrieveFlow(pipelineStream.config, workRetriever))
        .via(processFlow(pipelineStream.config, work => Future.fromTry(processMessage(work))))
    )

  private def processMessage(w: Work[Merged]): Try[List[Work[Denormalised]]] =
    w.data.collectionPath match {
      case None =>
        val denormalisedWork = w.transition[Denormalised](
          (Relations.none, Set.empty)
        )
        Success(List(denormalisedWork))

      case Some(CollectionPath(path, _)) =>
        pathsMsgSender.send(path).map(_ => List())
    }
}
