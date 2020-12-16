package weco.catalogue.transformer.example

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{Work, WorkState}
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
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
    with IdentifiersGenerators {

  implicit lazy val encoder: Encoder[CalmSourcePayload] =
    deriveConfiguredEncoder[CalmSourcePayload]

  override def withContext[R](
    testWith: TestWith[MemoryVersionedStore[S3ObjectLocation, ExampleData], R])
    : R =
    testWith(
      MemoryVersionedStore[S3ObjectLocation, ExampleData](
        initialEntries = Map.empty)
    )

  override def createPayload(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData])
    : CalmSourcePayload = {
    val data = ValidExampleData(id = createSourceIdentifier)
    val version = randomInt(from = 1, to = 10)

    val location = createS3ObjectLocation

    store.put(Version(location, version))(data) shouldBe a[Right[_, _]]

    CalmSourcePayload(
      id = data.id.toString,
      version = version,
      location = location)
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
    w.sourceIdentifier.toString shouldBe p.id
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
