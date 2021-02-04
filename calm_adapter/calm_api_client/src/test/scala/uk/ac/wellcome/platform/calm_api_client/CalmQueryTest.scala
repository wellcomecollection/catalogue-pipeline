package uk.ac.wellcome.platform.calm_api_client

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class CalmQueryTest extends AnyFunSpec with Matchers {

  it("a QueryLeaf serialises correctly") {
    new QueryLeaf(key = "Beep", value = "boop", relationalOperator = "<=").queryExpression shouldBe "(Beep<=boop)"
  }

  it("a QueryNode serialises correctly") {
    new QueryNode(
      left = new QueryLeaf(key = "Beep", value = "boop"),
      right = new QueryLeaf(key = "Buzz", value = "zap"),
      logicalOperator = "AND"
    ).queryExpression shouldBe "(Beep=boop)AND(Buzz=zap)"
  }

  it("a simple query tree serialises correctly") {
    val things: List[CalmQuery] =
      List("bing", "bong", "bang", "beep", "boop").map(v =>
        new QueryLeaf(key = "Key", value = v))
    things
      .reduce(_ or _)
      .queryExpression shouldBe "(Key=bing)OR(Key=bong)OR(Key=bang)OR(Key=beep)OR(Key=boop)"
  }

}
