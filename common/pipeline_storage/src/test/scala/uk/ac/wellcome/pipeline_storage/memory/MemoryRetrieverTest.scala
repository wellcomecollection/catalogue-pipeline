package uk.ac.wellcome.pipeline_storage.memory

import java.util.UUID

import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.pipeline_storage.{MemoryRetriever, Retriever, RetrieverTestCases}

class MemoryRetrieverTest extends RetrieverTestCases[Map[String, UUID], UUID] {
  override def withContext[R](
    documents: Seq[UUID])(testWith: TestWith[Map[String, UUID], R]): R =
    testWith(
      documents
        .map { doc => id.canonicalId(doc) -> doc }
        .toMap
    )

  override def withRetriever[R](testWith: TestWith[Retriever[UUID], R])(implicit index: Map[String, UUID]): R =
    testWith(
      new MemoryRetriever(index)
    )

  override def createT: UUID =
    UUID.randomUUID()

  override implicit val id: CanonicalId[UUID] =
    (uuid: UUID) => uuid.toString
}
