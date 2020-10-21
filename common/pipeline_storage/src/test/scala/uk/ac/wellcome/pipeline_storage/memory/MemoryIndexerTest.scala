package uk.ac.wellcome.pipeline_storage.memory

import org.scalatest.Assertion
import uk.ac.wellcome.fixtures.{RandomGenerators, TestWith}
import uk.ac.wellcome.pipeline_storage.fixtures.SampleDocument
import uk.ac.wellcome.pipeline_storage.{
  Indexer,
  IndexerTestCases,
  MemoryIndexer
}

import scala.collection.mutable

class MemoryIndexerTest
    extends IndexerTestCases[
      mutable.Map[String, SampleDocument],
      SampleDocument]
    with RandomGenerators {
  import SampleDocument._

  override def withContext[R](documents: Seq[SampleDocument])(
    testWith: TestWith[mutable.Map[String, SampleDocument], R]): R =
    testWith(
      mutable.Map(
        documents.map { doc =>
          (doc.canonicalId, doc)
        }: _*
      )
    )

  override def withIndexer[R](testWith: TestWith[Indexer[SampleDocument], R])(
    implicit index: mutable.Map[String, SampleDocument]): R =
    testWith(
      new MemoryIndexer[SampleDocument](index)
    )

  override def createDocumentWith(id: String, version: Int): SampleDocument =
    SampleDocument(
      canonicalId = id,
      version = version,
      title = randomAlphanumeric())

  override def assertIsIndexed(doc: SampleDocument)(
    implicit index: mutable.Map[String, SampleDocument]): Assertion =
    index(doc.canonicalId) shouldBe doc

  override def assertIsNotIndexed(doc: SampleDocument)(
    implicit index: mutable.Map[String, SampleDocument]): Assertion =
    index.get(doc.canonicalId) should not be Some(doc)
}
