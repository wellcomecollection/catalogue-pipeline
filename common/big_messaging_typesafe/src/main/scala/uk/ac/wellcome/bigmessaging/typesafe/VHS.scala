package uk.ac.wellcome.bigmessaging.typesafe

import java.util.UUID
import scala.util.{Failure, Success, Try}
import org.scanamo.{DynamoFormat, DynamoValue}
import org.scanamo.error.DynamoReadError
import org.scanamo.auto._
import com.typesafe.config.Config
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB

import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  ObjectLocationPrefix,
  Version
}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.maxima.Maxima

case class EmptyMetadata()

trait GetLocation {
  def getLocation(key: Version[String, Int]): Try[ObjectLocation]
}

class VHS[T](val hybridStore: VHSInternalStore[T])
    extends VersionedHybridStore[
      String,
      Int,
      ObjectLocation,
      T,
      EmptyMetadata
    ](hybridStore)
    with GetLocation {

  def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
    hybridStore.indexedStore.get(key) match {
      case Right(Identified(_, entry)) => Success(entry.typedStoreId)
      case Left(error)                 => Failure(error.e)
    }
}

class VHSInternalStore[T](
  prefix: ObjectLocationPrefix,
  indexStore: Store[
    Version[String, Int],
    HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]
  ] with Maxima[String, Int],
  dataStore: TypedStore[ObjectLocation, T]
) extends HybridStoreWithMaxima[String, Int, ObjectLocation, T, EmptyMetadata] {

  override val indexedStore = indexStore;
  override val typedStore = dataStore;

  override protected def createTypeStoreId(
    id: Version[String, Int]): ObjectLocation =
    prefix.asLocation(
      id.id.toString,
      id.version.toString,
      UUID.randomUUID().toString)
}

object VHSBuilder {

  // Scanamo auto derivation cannot derive format for EmptyMetadata correctly.
  // An underscore is used for variable name as the compiler thinks it is unused
  // even though it isn't.
  private implicit val _: DynamoFormat[EmptyMetadata] =
    new DynamoFormat[EmptyMetadata] {
      override def read(
        av: DynamoValue): Either[DynamoReadError, EmptyMetadata] =
        Right(EmptyMetadata())
      override def write(t: EmptyMetadata): DynamoValue =
        DynamoValue.fromMap(Map.empty)
    }

  def build[T](config: Config)(implicit codec: Codec[T]): VHS[T] =
    VHSBuilder.build(
      buildObjectLocationPrefix(config),
      DynamoBuilder.buildDynamoConfig(config, namespace = "vhs"),
      DynamoBuilder.buildDynamoClient(config),
      S3Builder.buildS3Client(config)
    )

  def build[T](objectLocationPrefix: ObjectLocationPrefix,
               dynamoConfig: DynamoConfig,
               dynamoClient: AmazonDynamoDB,
               s3Client: AmazonS3)(implicit codec: Codec[T]): VHS[T] = {
    implicit val s3 = s3Client;
    implicit val dynamo = dynamoClient;
    new VHS(
      new VHSInternalStore(
        objectLocationPrefix,
        new DynamoHashStore[
          String,
          Int,
          HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]
        ](dynamoConfig),
        S3TypedStore[T]
      )
    )
  }

  private def buildObjectLocationPrefix(config: Config) =
    ObjectLocationPrefix(
      path = config.getOrElse("aws.vhs.s3.globalPrefix")(default = ""),
      namespace = config.getOrElse("aws.vhs.s3.bucketName")(default = ""))
}
