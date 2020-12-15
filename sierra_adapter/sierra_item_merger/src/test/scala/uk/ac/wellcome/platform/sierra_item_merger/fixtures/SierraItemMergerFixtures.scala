package uk.ac.wellcome.platform.sierra_item_merger.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.sierra_item_merger.services.{
  SierraItemMergerUpdaterService,
  SierraItemMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

import scala.concurrent.ExecutionContext.Implicits.global

trait SierraItemMergerFixtures
    extends Akka
    with SQS
    with SierraAdapterHelpers
    with SourceVHSFixture {
  def withSierraUpdaterService[R](sourceVHS: SourceVHS[SierraTransformable])(
    testWith: TestWith[SierraItemMergerUpdaterService, R]): R = {
    val sierraUpdaterService = new SierraItemMergerUpdaterService(sourceVHS)
    testWith(sierraUpdaterService)
  }

  def withSierraWorkerService[R](queue: Queue,
                                 sourceVHS: SourceVHS[SierraTransformable] =
                                   createSourceVHS[SierraTransformable])(
    testWith: TestWith[(SierraItemMergerWorkerService[String],
                        MemoryMessageSender),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val messageSender = new MemoryMessageSender
        val workerService = new SierraItemMergerWorkerService(
          sqsStream = sqsStream,
          sierraItemMergerUpdaterService =
            new SierraItemMergerUpdaterService(sourceVHS),
          messageSender = messageSender
        )

        testWith((workerService, messageSender))
      }
    }
}
