package weco.catalogue.display_model.test.util

import io.circe.Encoder
import org.scalatest.Assertion
import weco.catalogue.display_model.work.DisplayWork
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.json.utils.JsonAssertions
import weco.http.json.DisplayJsonUtil
import weco.catalogue.display_model.Implicits._

trait JsonMapperTestUtil extends JsonAssertions {

  def assertObjectMapsToJson[T](value: T, expectedJson: String)(
    implicit encoder: Encoder[T]
  ): Assertion =
    assertJsonStringsAreEqual(DisplayJsonUtil.toJson(value), expectedJson)

  def assertWorkMapsToJson(work: Work.Visible[WorkState.Indexed],
                           expectedJson: String): Assertion =
    assertObjectMapsToJson(
      DisplayWork(work),
      expectedJson = expectedJson
    )
}
