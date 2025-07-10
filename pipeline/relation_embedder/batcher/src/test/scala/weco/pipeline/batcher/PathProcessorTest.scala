package weco.pipeline.batcher
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.batcher.models.{Batch, Selector}
import weco.pipeline.batcher.models.Selector.{Children, Descendents, Node, Tree}

import scala.concurrent.ExecutionContext.Implicits.global

class PathProcessorTest
  extends AnyFunSpec
    with Matchers
    with ScalaFutures {


  /** The following tests use paths representing this tree:
   * {{{
   * A
   * |
   * |-------------
   * |  |         |
   * B  C         E
   * |  |------   |---------
   * |  |  |  |   |  |  |  |
   * D  X  Y  Z   1  2  3  4
   * }}}
   */
  it("processes incoming paths into batches") {
    val pathsProcessor = new PathsProcessor(maxBatchSize = 10)
    val batcherResponse = pathsProcessor.process(Seq("A/B", "A/E/1"))

    whenReady(batcherResponse) { response: BatcherResponse  =>
      response.successes.size shouldBe 1
      response.successes.head.selectors should contain theSameElementsAs List(
                Node("A"),
                Children("A"),
                Children("A/E"),
                Descendents("A/B"),
                Descendents("A/E/1")
              )
    }
  }

  it("processes incoming paths into batches split per tree") {
    val pathsProcessor = new PathsProcessor(maxBatchSize = 10)
    val batcherResponse = pathsProcessor.process(Seq("A", "Other/Tree"))

    whenReady(batcherResponse) { response: BatcherResponse  =>
      val batches = response.successes
      batches.size shouldBe 2
      batchRoots(batches) shouldBe Set("A", "Other")
      batchWithRoot("A", batches) should contain theSameElementsAs List(
        Tree("A")
      )
      batchWithRoot("Other", batches) should contain theSameElementsAs List(
        Node("Other"),
        Children("Other"),
        Descendents("Other/Tree")
      )
    }
  }

  it("sends the whole tree when batch consists of too many selectors") {
    val pathsProcessor = new PathsProcessor(maxBatchSize = 3)
    val batcherResponse = pathsProcessor.process(Seq("A/B", "A/E/1"))

    whenReady(batcherResponse) { response: BatcherResponse  =>
      val batches = response.successes
      batches.size shouldBe 1
      batches.head shouldBe Batch(rootPath = "A", selectors = List(Tree("A")))
    }
  }

  def batchRoots(batches: Seq[Batch]): Set[String] =
    batches.map(_.rootPath).toSet

  def batchWithRoot(rootPath: String, batches: Seq[Batch]): List[Selector] =
    batches.find(_.rootPath == rootPath).get.selectors
}
