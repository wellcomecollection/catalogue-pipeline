package uk.ac.wellcome.platform.idminter.services

import io.circe.Json
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import scalikejdbc._
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.platform.idminter.database.MemoryIdentifiersDao
import uk.ac.wellcome.platform.idminter.fixtures.{FieldDescription, IdentifiersDatabase, WorkerServiceFixture}
import uk.ac.wellcome.storage.streaming.CodecInstances._

class IdMinterServiceTest
    extends FunSpec
    with IdentifiersDatabase
    with Eventually
    with IntegrationPatience
    with Matchers
    with WorkerServiceFixture {

  it("creates the Identifiers table in MySQL upon startup") {
    withLocalSqsQueue { queue =>
      withIdentifiersDatabase { identifiersTableConfig =>
        val identifiersDao = new MemoryIdentifiersDao()
        val messageSender = new MemoryBigMessageSender[Json]()
        withWorkerService(messageSender, queue, identifiersDao) { workerService =>
          val database: SQLSyntax =
            SQLSyntax.createUnsafely(identifiersTableConfig.database)
          val table: SQLSyntax =
            SQLSyntax.createUnsafely(identifiersTableConfig.tableName)

          val service = new IdMinterService[String](
            workerService = workerService,
            rdsClientConfig = rdsClientConfig,
            identifiersTableConfig = identifiersTableConfig
          )

          service.run()

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
