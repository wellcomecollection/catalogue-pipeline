package weco.catalogue.transformer.example

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{Work, WorkState}
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.transformer.{
  TransformerWorker,
  TransformerWorkerTestCases
}

class ExampleTransformerTest
    extends TransformerWorkerTestCases[
      MemoryVersionedStore[String, ExampleData],
      Version[String, Int],
      ExampleData
    ]
    with IdentifiersGenerators {

  implicit lazy val encoder: Encoder[Version[String, Int]] =
    deriveConfiguredEncoder[Version[String, Int]]

  override def withContext[R](
    testWith: TestWith[MemoryVersionedStore[String, ExampleData], R]): R =
    testWith(
      MemoryVersionedStore[String, ExampleData](initialEntries = Map.empty)
    )

  override def createPayload(
    implicit store: MemoryVersionedStore[String, ExampleData])
    : Version[String, Int] = {
    val data = ValidExampleData(id = createSourceIdentifier)
    val version = randomInt(from = 1, to = 10)

    val id: Version[String, Int] = Version(data.id.toString, version)

    store.put(id)(data) shouldBe a[Right[_, _]]

    id
  }

  override def createBadPayload(
    implicit store: MemoryVersionedStore[String, ExampleData])
    : Version[String, Int] = {
    val data = InvalidExampleData
    val version = randomInt(from = 1, to = 10)

    val id = Version(id = randomAlphanumeric(), version)

    store.put(id)(data) shouldBe a[Right[_, _]]

    id
  }

  override def id(p: Version[String, Int]): String = p.id

  override def assertMatches(p: Version[String, Int],
                             w: Work[WorkState.Source])(
    implicit context: MemoryVersionedStore[String, ExampleData]): Unit = {
    w.sourceIdentifier.toString shouldBe p.id
    w.version shouldBe p.version
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String])(
    testWith: TestWith[TransformerWorker[ExampleData, String], R])(
    implicit sourceStore: MemoryVersionedStore[String, ExampleData]): R =
    testWith(
      new ExampleTransformerWorker(
        pipelineStream = pipelineStream,
        sourceStore = sourceStore
      )
    )
}
