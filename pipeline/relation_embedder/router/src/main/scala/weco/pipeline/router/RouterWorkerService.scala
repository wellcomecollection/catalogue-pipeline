package weco.pipeline.router

import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.catalogue.internal_model.work.{CollectionPath, Relations, Work}
import weco.json.JsonUtil._
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.PipelineStorageStream._
import weco.pipeline_storage.{Indexable, PipelineStorageStream, Retriever}
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
        .via(
          processFlow(
            pipelineStream.config,
            work => Future.fromTry(processMessage(work))))
    )
  }

  private def processMessage(
    work: Work[Merged]): Try[List[Work[Denormalised]]] =
    (work.data.collectionPath, work.state.relations) match {
      // For TEI works relations are already populated based on
      // inner works extracted by the TEI transformer. We don't need
      // to repopulate them in the relation embedder.
      // We don't expect TEI works to have a collectionPath field populated.
      case (None, relations) =>
        Success(List(work.transition[Denormalised]((relations, Set.empty))))
      case (Some(CollectionPath(path)), relations)
          if relations == Relations.none =>
        pathsMsgSender.send(path).map(_ => Nil)
      case (collectionPath, relations) =>
        Failure(new RuntimeException(
          s"collectionPath: $collectionPath and relations: $relations are both populated in ${work.state.id}. This shouldn't be possible"))
    }
}
