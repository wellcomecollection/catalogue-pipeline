package weco.pipeline.merger.services

import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.{Done, NotUsed}
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.flows.FlowOps
import weco.pipeline.matcher.models.MatcherResult._
import weco.json.JsonUtil.{fromJson, toJson}
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline.matcher.models.{MatchedIdentifiers, MatcherResult, WorkIdentifier}
import weco.pipeline_storage.{Indexer, PipelineStorageConfig}
import weco.typesafe.Runnable

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Worker[T, Output] {
  def doWork(t: T): Output
}

object MergerWorker {
  type WorkOrImage = Either[Work[Merged], Image[Initial]]
  type WorkSet = Seq[Option[Work[Identified]]]
}

trait MergerWorker extends Worker[MatcherResult, Future[List[MergerWorker.WorkOrImage]]] {
  import MergerWorker._

  implicit val ec: ExecutionContext
  val mergerManager: MergerManager
  val sourceWorkLookup: IdentifiedWorkLookup

  def doWork(matcherResult: MatcherResult): Future[List[WorkOrImage]] = {
    getWorkSets(matcherResult)
      .map(workSets => workSets.filter(_.flatten.nonEmpty).flatMap(
        ws =>
          // We use the matcher result time as the "modified" time on
          // the merged works, because it reflects the last time the
          // matcher inspected the connections between these works.
          //
          // We *cannot* rely on the modified times of the individual
          // works -- this may cause us to drop updates if works
          // get unlinked.
          //
          // See https://github.com/wellcomecollection/docs/tree/8d83d75aba89ead23559584db2533e95ceb09200/rfcs/038-matcher-versioning
          applyMerge(ws, matcherResult.createdTime)
        )
      )
  }

  private def getWorkSets(matcherResult: MatcherResult): Future[List[WorkSet]] =
    Future.sequence {
      matcherResult.works.toList.map {
        matchedIdentifiers =>
          sourceWorkLookup.fetchAllWorks(matchedIdentifiers.identifiers.toList)
      }
    }

  private def applyMerge(
                          workSet: WorkSet,
                          matcherResultTime: Instant
                        ): Seq[WorkOrImage] =
    mergerManager
      .applyMerge(maybeWorks = workSet)
      .mergedWorksAndImagesWithTime(matcherResultTime)
}

class CommandLineMergerWorkerService(
  val sourceWorkLookup: IdentifiedWorkLookup,
  val mergerManager: MergerManager
)(val workIds: Option[String])(implicit val ec: ExecutionContext)
    extends Runnable
      with MergerWorker {

  import MergerWorker._

  def runWithIds(str: String): Future[Unit] = {
    val workIdentifiers = str.split(",").map { id =>
      WorkIdentifier(CanonicalId(id), 0)
    }.toSeq

    val matcherResult = MatcherResult(
      works = Set(
        MatchedIdentifiers(
          identifiers = workIdentifiers.toSet,
        )
      ),
      createdTime = Instant.now
    )
    doWork(matcherResult).map(printResults)
  }

  def run(): Future[Unit] =
    workIds match {
        case Some(ids) => runWithIds(ids)
        case None      => Future.failed(new RuntimeException("No work IDs provided"))
    }


  import io.circe.generic.auto._

  def printResults(results: List[WorkOrImage]): Unit = {
    results.foreach {
      case Left(work) =>
        info(toJson(work))
      case Right(image) =>
        info(toJson(image))
    }
  }
}

class MergerWorkerService[WorkDestination, ImageDestination](
  msgStream: SQSStream[NotificationMessage],
  val sourceWorkLookup: IdentifiedWorkLookup,
  val mergerManager: MergerManager,
  workOrImageIndexer: Indexer[Either[Work[Merged], Image[Initial]]],
  workMsgSender: MessageSender[WorkDestination],
  imageMsgSender: MessageSender[ImageDestination],
  config: PipelineStorageConfig
)(implicit val ec: ExecutionContext)
    extends Runnable
      with MergerWorker
      with FlowOps {

  import weco.pipeline_storage.Indexable._
  import weco.pipeline_storage.PipelineStorageStream._
  import MergerWorker._

  def run(): Future[Done] =
    for {
      _ <- workOrImageIndexer.init()
      _ <- msgStream.runStream(
        this.getClass.getSimpleName,
        source =>
          source
            .via(processFlow(config, processMessage))
            .via(
              broadcastAndMerge(batchIndexAndSendWorksAndImages, noOutputFlow)
            )
      )
    } yield Done

  val batchIndexAndSendWorksAndImages
    : Flow[(Message, List[WorkOrImage]), Message, NotUsed] =
    batchIndexAndSendFlow(config, sendWorkOrImage, workOrImageIndexer)


  private def processMessage(
    message: NotificationMessage
  ): Future[List[WorkOrImage]] =
    Future.fromTry(
        fromJson[MatcherResult](message.body)
    ).flatMap(doWork)

  private def sendWorkOrImage(workOrImage: WorkOrImage): Try[Unit] =
    workOrImage match {
      case Left(work)   => workMsgSender.send(workIndexable.id(work))
      case Right(image) => imageMsgSender.send(imageIndexable.id(image))
    }

}
