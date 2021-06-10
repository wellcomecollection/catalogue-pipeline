package weco.catalogue.tei.id_extractor.fixtures

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.fixtures.TestWith
import scalikejdbc._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryStore
import weco.catalogue.tei.id_extractor.database.{PathIdTableConfig, RDSClientConfig, TableProvisioner}
import weco.catalogue.tei.id_extractor.{FieldDescription, PathIdManager, PathIdTable}

trait PathIdDatabase
    extends Eventually
    with IntegrationPatience
    with Matchers
    with TableNameGenerators {

  val host = "localhost"
  val port = 3307
  val username = "root"
  val password = "password"
  val maxSize = 8

  def eventuallyTableExists(tableConfig: PathIdTableConfig): Assertion =
    eventually {
      val database: SQLSyntax = SQLSyntax.createUnsafely(tableConfig.database)
      val table: SQLSyntax = SQLSyntax.createUnsafely(tableConfig.tableName)

      val fields = NamedDB('default) readOnly { implicit session =>
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

      fields.sortBy(_.field) shouldBe Seq(
        FieldDescription(
          field = "id",
          dataType = "varchar(255)",
          nullable = "NO",
          key = "UNI"),
        FieldDescription(
          field = "path",
          dataType = "varchar(255)",
          nullable = "NO",
          key = "PRI"),
        FieldDescription(
          field = "timeModified",
          dataType = "datetime",
          nullable = "NO",
          key = "")
      ).sortBy(_.field)
    }

  val rdsClientConfig = RDSClientConfig(
    primaryHost = host,
    replicaHost = host,
    port = port,
    username = username,
    password = password
  )

  def withPathIdDatabase[R](
    testWith: TestWith[PathIdTableConfig, R]): R = {
    ConnectionPool.add(
      'default,
      s"jdbc:mysql://$host:$port",
      username,
      password,
      settings = ConnectionPoolSettings(maxSize = maxSize)
    )


    implicit val session = AutoSession
    val databaseName: String = createDatabaseName
    val tableName: String = createTableName

    val pathIdDatabase: SQLSyntax = SQLSyntax.createUnsafely(databaseName)

    val pathIdTableConfig = PathIdTableConfig(
      database = databaseName,
      tableName = tableName
    )

    try {
      sql"CREATE DATABASE $pathIdDatabase".execute().apply()

      testWith(pathIdTableConfig)
    } finally {
      NamedDB('default) localTx { implicit session =>
        sql"DROP DATABASE IF EXISTS $pathIdDatabase".execute().apply()
      }

      session.close()
    }

  }

  def withPathIdTable[R](testWith: TestWith[(PathIdTableConfig,PathIdTable), R]): R = {
    withPathIdDatabase { config =>
      val table = new PathIdTable(config)
      testWith((config,table))
    }
    }

  def withInitializedPathIdTable[R](testWith: TestWith[PathIdTable, R]): R = {
    withPathIdTable { case (config,table) =>
      val provisioner = new TableProvisioner(rdsClientConfig)(
        database = config.database,
        tableName = config.tableName
      )

        provisioner
          .provision()
        eventuallyTableExists(config)

      testWith(table)
    }
  }

  def withPathIdManager[R](table: PathIdTable, bucket: Bucket)(testWith: TestWith[(PathIdManager[String], MemoryStore[S3ObjectLocation, String], MemoryMessageSender), R]) = {
    val store = new MemoryStore[S3ObjectLocation, String](Map())
    val messageSender: MemoryMessageSender = new MemoryMessageSender()
    val manager = new PathIdManager(table, store, messageSender, bucket.name)
    testWith((manager, store, messageSender))
  }


}
