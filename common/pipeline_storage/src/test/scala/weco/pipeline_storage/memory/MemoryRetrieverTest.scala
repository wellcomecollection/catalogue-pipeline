package weco.pipeline_storage.memory

import java.util.UUID
import weco.elasticsearch.model.IndexId
import weco.fixtures.TestWith
import weco.pipeline_storage.{Retriever, RetrieverTestCases}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class MemoryRetrieverTest extends RetrieverTestCases[Map[String, UUID], UUID] {
  override def withContext[R](
    documents: Seq[UUID]
  )(testWith: TestWith[Map[String, UUID], R]): R =
    testWith(
      documents.map {
        doc =>
          id.indexId(doc) -> doc
      }.toMap
    )

  override def withRetriever[R](testWith: TestWith[Retriever[UUID], R])(
    implicit index: Map[String, UUID]
  ): R =
    testWith(
      new MemoryRetriever(mutable.Map(index.toSeq: _*))
    )

  override def createT: UUID =
    UUID.randomUUID()

  override implicit val id: IndexId[UUID] =
    (uuid: UUID) => uuid.toString
}
