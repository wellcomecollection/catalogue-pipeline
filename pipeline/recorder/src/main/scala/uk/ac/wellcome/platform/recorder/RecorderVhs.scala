package uk.ac.wellcome.platform.recorder

import java.util.UUID
import scala.util.{Failure, Success, Try}
import org.scanamo.{DynamoFormat, DynamoValue}
import org.scanamo.error.DynamoReadError
import org.scanamo.auto._
import com.typesafe.config.Config
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.dynamo.DynamoConfig 
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStoreWithMaxima,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  ObjectLocationPrefix,
  Version
}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec._

case class EmptyMetadata()

trait GetLocation {
  def getLocation(key: Version[String, Int]): Try[ObjectLocation]
}

class RecorderVhs(hybridStore: RecorderHybridStore)
    extends VersionedHybridStore[
      String,
      Int,
      ObjectLocation,
      TransformedBaseWork,
      EmptyMetadata
    ](hybridStore)
    with GetLocation {

  def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
    hybridStore.indexedStore.get(key) match {
      case Right(Identified(_, entry)) => Success(entry.typedStoreId)
      case Left(error)                 => Failure(error.e)
    }
}

class RecorderHybridStore(
  prefix: ObjectLocationPrefix,
  dynamoIndexStore: DynamoHashStore[
    String,
    Int,
    HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]],
  s3TypedStore: S3TypedStore[TransformedBaseWork]
) extends HybridStoreWithMaxima[
      String,
      Int,
      ObjectLocation,
      TransformedBaseWork,
      EmptyMetadata] {

  override val indexedStore = dynamoIndexStore;
  override val typedStore = s3TypedStore;

  override protected def createTypeStoreId(
    id: Version[String, Int]): ObjectLocation =
    prefix.asLocation(
      id.id.toString,
      id.version.toString,
      UUID.randomUUID().toString)
}

object RecorderVhs {

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

  def build(config: Config): RecorderVhs = {
    // TODO: from where do we get the correct values for this?
    val objectLocationPrefix = ObjectLocationPrefix("namespace", "path")
    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = "namespace")
    implicit val s3Client = S3Builder.buildS3Client(config)
    implicit val dynamoClient = DynamoBuilder.buildDynamoClient(config)
    RecorderVhs.build(
      objectLocationPrefix,
      dynamoConfig,
      dynamoClient,
      s3Client)
  }

  def build(objectLocationPrefix: ObjectLocationPrefix,
            dynamoConfig: DynamoConfig,
            dynamoClient: AmazonDynamoDB,
            s3Client: AmazonS3): RecorderVhs = {
    new RecorderVhs(
      new RecorderHybridStore(
        objectLocationPrefix,
        RecorderVhs.buildIndexStore(dynamoClient, dynamoConfig),
        RecorderVhs.buildTypedStore(s3Client)
      )
    )
  }

  def buildIndexStore(dynamoClient: AmazonDynamoDB,
                      dynamoConfig: DynamoConfig) = {
    implicit val client = dynamoClient
    new DynamoHashStore[
      String, 
      Int,
      HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]
    ](dynamoConfig)
  }

  def buildTypedStore(s3Client: AmazonS3) = {
    implicit val client = s3Client
    S3TypedStore[TransformedBaseWork]
  }
}
