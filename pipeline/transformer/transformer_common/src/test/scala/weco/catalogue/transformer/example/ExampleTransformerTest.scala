package weco.catalogue.transformer.example

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.scalatest.EitherValues
import weco.json.JsonUtil._
import weco.fixtures.TestWith
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.Version
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryVersionedStore
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.transformer.{
  TransformerWorker,
  TransformerWorkerTestCases
}

import scala.concurrent.ExecutionContext.Implicits.global

class ExampleTransformerTest
    extends TransformerWorkerTestCases[
      MemoryVersionedStore[S3ObjectLocation, ExampleData],
      CalmSourcePayload,
      ExampleData
    ]
    with S3ObjectLocationGenerators
    with IdentifiersGenerators
    with EitherValues {

  implicit lazy val encoder: Encoder[CalmSourcePayload] =
    deriveConfiguredEncoder[CalmSourcePayload]

  override def withContext[R](
    testWith: TestWith[MemoryVersionedStore[S3ObjectLocation, ExampleData], R])
    : R =
    testWith(
      MemoryVersionedStore[S3ObjectLocation, ExampleData](
        initialEntries = Map.empty)
    )

  override def createPayloadWith(id: String, version: Int)(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData])
    : CalmSourcePayload = {
    val data = ValidExampleData(
      id = createSourceIdentifierWith(
        identifierType = IdentifierType.CalmRecordIdentifier,
        value = id
      ),
      title = randomAlphanumeric()
    )

    val location = createS3ObjectLocation

    store.put(Version(location, version))(data) shouldBe a[Right[_, _]]

    CalmSourcePayload(id = id, version = version, location = location)
  }

  override def setPayloadVersion(p: CalmSourcePayload, version: Int)(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData])
    : CalmSourcePayload = {
    val storedData: ExampleData =
      store.get(Version(p.location, p.version)).value.identifiedT

    val location = createS3ObjectLocation
    store.put(Version(location, version))(storedData) shouldBe a[Right[_, _]]

    p.copy(location = location, version = version)
  }

  override def createBadPayload(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData])
    : CalmSourcePayload = {
    val data = InvalidExampleData
    val version = randomInt(from = 1, to = 10)

    val location = createS3ObjectLocation

    store.put(Version(location, version))(data) shouldBe a[Right[_, _]]

    CalmSourcePayload(
      id = randomAlphanumeric(),
      version = version,
      location = location)
  }

  override def assertMatches(p: CalmSourcePayload, w: Work[WorkState.Source])(
    implicit context: MemoryVersionedStore[S3ObjectLocation, ExampleData])
    : Unit = {
    w.sourceIdentifier.value shouldBe p.id
    w.version shouldBe p.version
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String],
    retriever: Retriever[Work[WorkState.Source]]
  )(testWith: TestWith[
      TransformerWorker[CalmSourcePayload, ExampleData, String],
      R])(
    implicit sourceStore: MemoryVersionedStore[S3ObjectLocation, ExampleData])
    : R =
    testWith(
      new ExampleTransformerWorker(
        pipelineStream = pipelineStream,
        sourceStore = sourceStore,
        retriever = retriever
      )
    )
}
