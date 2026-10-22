package weco.pipeline.merger

class MergerSQSLambdaBatchedTest extends MergerSQSLambdaTest {
  override protected val batched: Boolean = true
}
