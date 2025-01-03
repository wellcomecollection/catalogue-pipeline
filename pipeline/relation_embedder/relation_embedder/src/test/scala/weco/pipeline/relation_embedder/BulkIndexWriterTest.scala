package weco.pipeline.relation_embedder

import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.pipeline_storage.elastic.ElasticIndexer

import scala.concurrent.ExecutionContext.Implicits.global
import weco.catalogue.internal_model.Implicits._
import weco.pipeline.relation_embedder.fixtures.BulkWriterAssertions
import weco.pipeline_storage.memory.MemoryIndexer

import scala.collection.mutable

class BulkIndexWriterTest
    extends AnyFunSpec
    with IndexFixtures
    with BulkWriterAssertions {

  it("fails to initialise if it cannot access the index") {
    val workIndexer =
      new ElasticIndexer[Work[Denormalised]](
        client = elasticClient,
        index = Index("this-index-does-not-exist")
      )
    val caught = intercept[RuntimeException] {
      new BulkIndexWriter(workIndexer = workIndexer, maxBatchWeight = 1)
    }
    caught.getMessage should include(
      "Indexer Initialisation error looking for index: this-index-does-not-exist"
    )
  }

  it("writes all given Works to the index in appropriately sized batches") {
    val denormalisedIndex =
      mutable.Map.empty[String, Work[Denormalised]]

    implicit val bulkWriter: BulkWriter = new BulkIndexWriter(
      workIndexer = new MemoryIndexer(denormalisedIndex),
      maxBatchWeight = 3
    )
    assertWhenWritingCompleted(works(5)) {
      result: Seq[Seq[Work[Denormalised]]] =>
        denormalisedIndex.size shouldBe 5
        // The specifics of batching up are more thoroughly examined in BulkWriterTest
        result.length shouldBe 2
    }

  }
}
