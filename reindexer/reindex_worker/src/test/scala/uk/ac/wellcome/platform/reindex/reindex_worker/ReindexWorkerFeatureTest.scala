package uk.ac.wellcome.platform.reindex.reindex_worker

import com.gu.scanamo.Scanamo
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{CompleteReindexParameters, PartialReindexParameters}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.vhs.Entry

class ReindexWorkerFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with ScalaFutures
    with WorkerServiceFixture {

  private def createEntries: Seq[ReindexerEntry] =
    (1 to 4).map(i => {
      Entry(
        id = s"id$i",
        location = ObjectLocation(
          namespace = "s3://example-bukkit",
          key = s"id$i"
        ),
        version = 1,
        metadata = ReindexerMetadata(name = s"example$i")
      )
    })

  private def createReindexableData(table: Table): Seq[ReindexerEntry] = {
    val testRecords = createEntries

    testRecords.foreach { testRecord =>
      Scanamo.put(dynamoDbClient)(table.name)(testRecord)
    }
    testRecords
  }

  it("sends a notification for every record that needs a reindex") {
    val messageSender = new MemoryIndividualMessageSender()
    val destination = "reindexes"

    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        withWorkerService(queue, table, messageSender, destination) { _ =>
          val testRecords = createReindexableData(table)

          val reindexParameters =
            CompleteReindexParameters(segment = 0, totalSegments = 1)

          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            messageSender.getMessages[ReindexerEntry]() should contain theSameElementsAs testRecords
          }
        }
      }
    }
  }

  it("can handle a partial reindex of the table") {
    val messageSender = new MemoryIndividualMessageSender()
    val destination = "reindexes"

    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        withWorkerService(queue, table, messageSender, destination) { _ =>
          val testRecords = createReindexableData(table)

          val reindexParameters = PartialReindexParameters(maxRecords = 1)

          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            messageSender.getMessages[ReindexerEntry]() shouldBe Seq(testRecords.head)
          }
        }
      }
    }
  }

  it("includes all the information on a row") {
    case class ExpandedEntry(
                              id: String,
                              location: ObjectLocation,
                              version: Int,
                              metadata: ReindexerMetadata,
                              index: Int
                            )

    val entries = createEntries

    val expandedEntries: Seq[ExpandedEntry] =
      entries.zipWithIndex.map { case (entry, index) =>
        ExpandedEntry(
          id = entry.id,
          location = entry.location,
          version = entry.version,
          metadata = entry.metadata,
          index = index
        )
      }

    val messageSender = new MemoryIndividualMessageSender()
    val destination = "reindexes"

    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        expandedEntries.foreach { record =>
          Scanamo.put(dynamoDbClient)(table.name)(record)
        }
        withWorkerService(queue, table, messageSender, destination) { _ =>
          val reindexParameters = CompleteReindexParameters(
            segment = 0,
            totalSegments = 1
          )

          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            messageSender.getMessages[ReindexerEntry]() should contain theSameElementsAs entries
            messageSender.getMessages[ExpandedEntry]() should contain theSameElementsAs expandedEntries
          }
        }
      }
    }
  }
}
