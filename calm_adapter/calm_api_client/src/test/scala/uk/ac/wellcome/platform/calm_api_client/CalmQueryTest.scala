package uk.ac.wellcome.platform.calm_api_client

import java.time.LocalDate
import java.util.UUID

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

  it("dates are formatted correctly") {
    val date = LocalDate.of(1917, 2, 23)
    val formattedDate = CalmQuery.formatDate(date)
    formattedDate shouldBe "23/02/1917"
  }

  it("RecordIds are wrapped in double quotes when serialised") {
    val id = UUID.randomUUID().toString
    val idQuery = CalmQuery.RecordId(id)
    idQuery.queryExpression shouldBe s"""(RecordId="$id")"""
  }

}
