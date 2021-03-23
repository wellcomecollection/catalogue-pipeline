package uk.ac.wellcome.pipeline_storage.memory

import org.scalatest.Assertion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.pipeline_storage.{
  Indexer,
  IndexerTestCases,
  MemoryIndexer
}
import weco.catalogue.pipeline_storage.generators.{
  SampleDocument,
  SampleDocumentGenerators
}

import scala.collection.mutable

class MemoryIndexerTest
    extends IndexerTestCases[
      mutable.Map[String, SampleDocument],
      SampleDocument]
    with SampleDocumentGenerators {
  import SampleDocument._

  override def withContext[R](documents: Seq[SampleDocument])(
    testWith: TestWith[mutable.Map[String, SampleDocument], R]): R =
    testWith(
      mutable.Map(
        documents.map { doc =>
          (doc.id, doc)
        }: _*
      )
    )

  override def withIndexer[R](testWith: TestWith[Indexer[SampleDocument], R])(
    implicit index: mutable.Map[String, SampleDocument]): R =
    testWith(
      new MemoryIndexer[SampleDocument](index)
    )

  override def createDocument: SampleDocument =
    createDocumentWith()

  override def assertIsIndexed(doc: SampleDocument)(
    implicit index: mutable.Map[String, SampleDocument]): Assertion =
    index(doc.id) shouldBe doc

  override def assertIsNotIndexed(doc: SampleDocument)(
    implicit index: mutable.Map[String, SampleDocument]): Assertion =
    index.get(doc.id) should not be Some(doc)
}
