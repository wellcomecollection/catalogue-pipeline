package uk.ac.wellcome.platform.idminter.services

import io.circe.Json
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import scalikejdbc._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.platform.idminter.database.{FieldDescription, SQLIdentifiersDao}
import uk.ac.wellcome.platform.idminter.fixtures.{IdentifiersDatabase, WorkerServiceFixture}
import uk.ac.wellcome.storage.streaming.CodecInstances._

class IdMinterWorkerServiceTest
    extends FunSpec
    with Messaging
    with IdentifiersDatabase
    with Eventually
    with IntegrationPatience
    with Matchers
    with MockitoSugar
    with WorkerServiceFixture {

  it("creates the Identifiers table in MySQL upon startup") {
    withLocalSqsQueue { queue =>
      withLocalSnsTopic { topic =>
        withIdentifiersDatabase { identifiersTableConfig =>
          withLocalS3Bucket { bucket =>
            val identifiersDao = mock[SQLIdentifiersDao]
            val messageSender = new MemoryBigMessageSender[Json]()
            withWorkerService(
              bucket,
              messageSender,
              queue,
              identifiersDao,
              identifiersTableConfig) { _ =>
              val database: SQLSyntax =
                SQLSyntax.createUnsafely(identifiersTableConfig.database)
              val table: SQLSyntax =
                SQLSyntax.createUnsafely(identifiersTableConfig.tableName)

              eventually {
                val fields = DB readOnly { implicit session =>
                  sql"DESCRIBE $database.$table"
                    .map(
                      rs =>
                        FieldDescription(
                          rs.string("Field"),
                          rs.string("Type"),
                          rs.string("Null"),
                          rs.string("Key")))
                    .list()
                    .apply()
                }

                fields.length should be > 0
              }
            }
          }
        }
      }
    }
  }
}
