package weco.pipeline_storage.memory

import org.scalatest.Assertion
import weco.fixtures.TestWith
import weco.pipeline_storage.generators.{
  SampleDocument,
  SampleDocumentGenerators
}
import weco.pipeline_storage.{Indexer, IndexerTestCases}

import scala.collection.mutable

class MemoryIndexerTest
    extends IndexerTestCases[
      mutable.Map[String, SampleDocument],
      SampleDocument
    ]
    with SampleDocumentGenerators {
  import weco.pipeline_storage.generators.SampleDocument._

  override def withContext[R](
    documents: Seq[SampleDocument]
  )(testWith: TestWith[mutable.Map[String, SampleDocument], R]): R =
    testWith(
      mutable.Map(
        documents.map {
          doc =>
            (doc.id, doc)
        }: _*
      )
    )

  override def withIndexer[R](testWith: TestWith[Indexer[SampleDocument], R])(
    implicit index: mutable.Map[String, SampleDocument]
  ): R =
    testWith(
      new MemoryIndexer[SampleDocument](index)
    )

  override def createDocument: SampleDocument =
    createDocumentWith()

  override def assertIsIndexed(doc: SampleDocument)(
    implicit index: mutable.Map[String, SampleDocument]
  ): Assertion =
    index(doc.id) shouldBe doc

  override def assertIsNotIndexed(doc: SampleDocument)(
    implicit index: mutable.Map[String, SampleDocument]
  ): Assertion =
    index.get(doc.id) should not be Some(doc)
}
