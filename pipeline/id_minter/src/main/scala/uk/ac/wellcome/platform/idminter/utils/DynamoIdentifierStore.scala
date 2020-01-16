package uk.ac.wellcome.platform.idminter.utils

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import grizzled.slf4j.Logging
import org.scanamo._
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.ScanamoOps
import uk.ac.wellcome.storage.dynamo.DynamoConfig

import scala.util.{Failure, Success, Try}
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models._
import org.scanamo.syntax._

class DynamoIdentifierStore(
                            val config: DynamoConfig
                          )(
                            implicit
                              val client: AmazonDynamoDB,
                              val identifierFormat: DynamoFormat[Identifier],
                              val sourceIdentifierFormat: DynamoFormat[SourceIdentifier]
) extends Logging {

  val table: Table[Identifier] = Table[Identifier](config.tableName)

  def put(identifier: Identifier): Either[IdentifierError, Identifier] = {
    val ops = table.given(not(attributeExists('id))).put(identifier)
    val result = Try(Scanamo(client).exec(ops))

    result match {
      case Success(Right(_))  => Right(identifier)
      case Success(Left(err)) => Left(IdentifierWriteError(err))
      case Failure(err)       => Left(IdentifierWriteError(err))
    }
  }

  def getByCanonicalId(id: String): Either[IdentifierError, Identifier] =
    runQuery(
      table.query('id -> id)
    )

  def getBySourceIdentifier(sourceIdentifier: SourceIdentifier): Either[IdentifierError, Identifier] =
    runQuery(
      table.index("sourceIdentifier")
      .query('sourceIdentifier -> sourceIdentifier)
    )

  protected def runQuery(ops: ScanamoOps[List[Either[DynamoReadError, Identifier]]]): Either[IdentifierError, Identifier] =
    Try(Scanamo(client).exec(ops)) match {
      case Success(List(Right(entry))) => Right(entry)
      case Success(List(Left(err))) => Left(IdentifierReadError(
        new Error(s"DynamoReadError: ${err.toString}"))
      )
      case Success(list) if list.length > 1 => Left(IdentifierAmbiguousError())
      case Success(Nil) => Left(IdentifierDoesNotExistError())
      case Failure(err) => Left(IdentifierReadError(err))
    }
}