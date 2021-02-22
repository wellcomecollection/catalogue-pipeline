package weco.catalogue.sierra_linker.holdings

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.sierra_adapter.model.{
  SierraHoldingsNumber,
  SierraHoldingsRecord
}
import uk.ac.wellcome.storage.store.VersionedStore
import weco.catalogue.sierra_linker.{
  LinkingRecord,
  SierraLinkerWorkerService,
  SierraLinkerWorkerServiceTestCases
}

class HoldingsLinkerWorkerServiceTest
    extends SierraLinkerWorkerServiceTestCases[
      SierraHoldingsNumber,
      SierraHoldingsRecord]
    with HoldingsLinkerFixtures
    with Akka {
  override implicit val decoder: Decoder[SierraHoldingsRecord] = deriveDecoder
  override implicit val encoder: Encoder[SierraHoldingsRecord] = deriveEncoder

  override def withWorkerService[R](
    queue: SQS.Queue,
    store: VersionedStore[SierraHoldingsNumber, Int, LinkingRecord],
    messageSender: MemoryMessageSender,
    metrics: MemoryMetrics = new MemoryMetrics)(
    testWith: TestWith[SierraLinkerWorkerService[SierraHoldingsNumber,
                                                 SierraHoldingsRecord,
                                                 String],
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val workerService = new SierraLinkerWorkerService(
          sqsStream = sqsStream,
          linkStore = new HoldingsLinkingRecordStore(store),
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
