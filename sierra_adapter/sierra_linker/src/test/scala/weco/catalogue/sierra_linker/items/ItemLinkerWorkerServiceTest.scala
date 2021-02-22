package weco.catalogue.sierra_linker.items

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.sierra_adapter.model.{SierraItemNumber, SierraItemRecord}
import uk.ac.wellcome.storage.store.VersionedStore
import weco.catalogue.sierra_linker.{
  LinkingRecord,
  SierraLinkerWorkerService,
  SierraLinkerWorkerServiceTestCases
}

class ItemLinkerWorkerServiceTest
    extends SierraLinkerWorkerServiceTestCases[
      SierraItemNumber,
      SierraItemRecord]
    with ItemLinkerFixtures
    with Akka {
  override implicit val decoder: Decoder[SierraItemRecord] = deriveDecoder
  override implicit val encoder: Encoder[SierraItemRecord] = deriveEncoder

  override def withWorkerService[R](
    queue: SQS.Queue,
    store: VersionedStore[SierraItemNumber, Int, LinkingRecord],
    messageSender: MemoryMessageSender,
    metrics: MemoryMetrics = new MemoryMetrics)(
    testWith: TestWith[
      SierraLinkerWorkerService[SierraItemNumber, SierraItemRecord, String],
      R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val workerService = new SierraLinkerWorkerService(
          sqsStream = sqsStream,
          linkStore = new ItemLinkingRecordStore(store),
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
