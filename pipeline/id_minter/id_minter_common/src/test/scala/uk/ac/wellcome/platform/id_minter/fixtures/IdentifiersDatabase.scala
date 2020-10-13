package uk.ac.wellcome.platform.id_minter.fixtures

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.fixtures.TestWith
import scalikejdbc._
import uk.ac.wellcome.platform.id_minter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import uk.ac.wellcome.platform.id_minter.database.FieldDescription
import uk.ac.wellcome.platform.id_minter.generators.TableNameGenerators

trait IdentifiersDatabase
    extends Eventually
    with IntegrationPatience
    with Matchers
    with TableNameGenerators {

  val host = "localhost"
  val port = 3307
  val username = "root"
  val password = "password"
  val maxSize = 8

  def eventuallyTableExists(tableConfig: IdentifiersTableConfig): Assertion =
    eventually {
      val database: SQLSyntax = SQLSyntax.createUnsafely(tableConfig.database)
      val table: SQLSyntax = SQLSyntax.createUnsafely(tableConfig.tableName)

      val fields = NamedDB('primary) readOnly { implicit session =>
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
          field = "CanonicalId",
          dataType = "varchar(255)",
          nullable = "NO",
          key = "PRI"),
        FieldDescription(
          field = "OntologyType",
          dataType = "varchar(255)",
          nullable = "NO",
          key = "MUL"),
        FieldDescription(
          field = "SourceSystem",
          dataType = "varchar(255)",
          nullable = "NO",
          key = ""),
        FieldDescription(
          field = "SourceId",
          dataType = "varchar(255)",
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

  def withIdentifiersDatabase[R](
    testWith: TestWith[IdentifiersTableConfig, R]): R = {
    ConnectionPool.add(
      'primary,
      s"jdbc:mysql://$host:$port",
      username,
      password,
      settings = ConnectionPoolSettings(maxSize = maxSize)
    )
    ConnectionPool.add(
      'replica,
      s"jdbc:mysql://$host:$port",
      username,
      password,
      settings = ConnectionPoolSettings(maxSize = maxSize)
    )

    implicit val session = NamedAutoSession('primary)

    val databaseName: String = createDatabaseName
    val tableName: String = createTableName

    val identifiersDatabase: SQLSyntax = SQLSyntax.createUnsafely(databaseName)

    val identifiersTableConfig = IdentifiersTableConfig(
      database = databaseName,
      tableName = tableName
    )

    try {
      sql"CREATE DATABASE $identifiersDatabase".execute().apply()

      testWith(identifiersTableConfig)
    } finally {
      NamedDB('primary) localTx { implicit session =>
        sql"DROP DATABASE IF EXISTS $identifiersDatabase".execute().apply()
      }

      session.close()
    }

  }
}
