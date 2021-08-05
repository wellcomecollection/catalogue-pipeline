package weco.pipeline.transformer.sierra

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicSessionCredentials}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import software.amazon.awssdk.auth.credentials.{AwsSessionCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.sierra.identifiers.SierraBibNumber
import weco.json.JsonUtil._
import weco.storage.dynamo.DynamoConfig
import weco.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import weco.storage.store.dynamo.{DynamoHashStore, DynamoHybridStore}
import weco.storage.store.s3.S3TypedStore
import weco.storage.store.{HybridStoreWithMaxima, VersionedHybridStore}
import weco.storage.{Identified, Version}

import scala.language.higherKinds

class SierraLiveDataTransformerTest extends AnyFunSpec with Matchers with EitherValues {

  /** This "test" will fetch a Sierra record from the VHS using your local credentials
    * and transform it with your local transformer.
    *
    * It's meant for debugging the transformer process, not automated tests.
    *
    */
  ignore("transforms a live record") {
    val tableName = "vhs-sierra-sierra-adapter-20200604"
    val bibNumber = SierraBibNumber("3043873")

    val identifiedT = getSierraTransformable(tableName, bibNumber)

    val version = identifiedT.id.version
    val transformable = identifiedT.identifiedT

    val work = SierraTransformer(transformable, version)

    println(work)
  }

  private def getSierraTransformable(tableName: String, bibNumber: SierraBibNumber): Identified[Version[String, Int], SierraTransformable] = {
    val assumeRoleRequest =
      AssumeRoleRequest.builder()
        .roleArn("arn:aws:iam::760097843905:role/platform-read_only")
        .roleSessionName("SierraTransformerTests")
        .build()

    val stsClient = StsClient.builder().build()

    val credentials =
      stsClient.assumeRole(assumeRoleRequest).credentials()

    val sessionCredentials =
      StaticCredentialsProvider.create(
        AwsSessionCredentials.create(
          credentials.accessKeyId(),
          credentials.secretAccessKey(),
          credentials.sessionToken()
        )
      )

    implicit val dynamoClient: DynamoDbClient =
      DynamoDbClient.builder()
        .credentialsProvider(sessionCredentials)
        .build()

    implicit val s3Client: AmazonS3 =
      AmazonS3ClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(
          new BasicSessionCredentials(
            credentials.accessKeyId(),
            credentials.secretAccessKey(),
            credentials.sessionToken()
          )
        ))
        .withPathStyleAccessEnabled(true)
        .build()

    implicit val typedStore: S3TypedStore[SierraTransformable] = S3TypedStore[SierraTransformable]

    implicit val indexedStore = new DynamoHashStore[String, Int, S3ObjectLocation](
      config = DynamoConfig(tableName = tableName)
    )

    class VHSInternalStore(prefix: S3ObjectLocationPrefix)(
      implicit
      indexedStore: DynamoHashStore[String, Int, S3ObjectLocation],
      typedStore: S3TypedStore[SierraTransformable]
    ) extends DynamoHybridStore[SierraTransformable](prefix)(indexedStore, typedStore)
      with HybridStoreWithMaxima[String, Int, S3ObjectLocation, SierraTransformable]

    val vhs =
      new VersionedHybridStore[String, Int, S3ObjectLocation, SierraTransformable](
        new VHSInternalStore(
          prefix = S3ObjectLocationPrefix("", "")
        )
      )

    vhs.getLatest(bibNumber.withoutCheckDigit).value
  }
}
