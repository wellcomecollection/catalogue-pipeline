package weco.catalogue.sierra_merger.fixtures

import io.circe.Decoder
import uk.ac.wellcome.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import weco.storage.streaming.Codec._
import weco.catalogue.sierra_merger.models.{RecordOps, TransformableOps}
import weco.catalogue.sierra_merger.services.{Updater, Worker}
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraTransformable
}
import weco.catalogue.source_model.store.SourceVHS

import scala.concurrent.ExecutionContext.Implicits.global

trait RecordMergerFixtures extends Akka with SQS with SourceVHSFixture {

  def withRunningWorker[Record <: AbstractSierraRecord[_], R](
    queue: Queue,
    sourceVHS: SourceVHS[SierraTransformable] =
      createSourceVHS[SierraTransformable]
  )(
    testWith: TestWith[(Worker[Record, String], MemoryMessageSender), R]
  )(
    implicit
    decoder: Decoder[Record],
    transformableOps: TransformableOps[Record],
    recordOps: RecordOps[Record]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val messageSender = new MemoryMessageSender
        val workerService = new Worker(
          sqsStream = sqsStream,
          updater = new Updater[Record](sourceVHS),
          messageSender = messageSender
        )

        workerService.run()

        testWith((workerService, messageSender))
      }
    }
}
