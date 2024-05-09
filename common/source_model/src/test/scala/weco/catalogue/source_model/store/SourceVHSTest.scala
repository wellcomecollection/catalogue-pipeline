package weco.catalogue.source_model.store

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil._
import weco.storage.{UpdateNoSourceError, Version}
import weco.catalogue.source_model.fixtures.SourceVHSFixture

class SourceVHSTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with SourceVHSFixture {
  case class Shape(sides: Int, colour: String)

  it("adds the S3 location to the result of putLatest()") {
    val store = createSourceVHS[Shape]

    val shape = Shape(sides = 4, colour = "red")

    val result = store.putLatest(id = "redsquare")(shape).value

    result.id shouldBe Version("redsquare", version = 0)
    val (location, storedShape) = result.identifiedT

    storedShape shouldBe shape
    store.underlying.hybridStore.typedStore
      .get(location)
      .value
      .identifiedT shouldBe shape
  }

  it("adds the S3 location to the result of update()") {
    val store = createSourceVHS[Shape]

    val shape = Shape(sides = 4, colour = "blue")
    store.putLatest(id = "bluething")(shape) shouldBe a[Right[_, _]]

    val result = store
      .update("bluething") {
        storedShape =>
          Right(storedShape.copy(sides = storedShape.sides + 1))
      }
      .value

    result.id shouldBe Version("bluething", version = 1)
    val (location, storedShape) = result.identifiedT

    storedShape shouldBe Shape(sides = 5, colour = "blue")
    store.underlying.hybridStore.typedStore
      .get(location)
      .value
      .identifiedT shouldBe Shape(sides = 5, colour = "blue")
  }

  it("wraps an error from an update()") {
    val store = createSourceVHS[Shape]

    val err = store
      .update("doesNotExist") {
        storedShape =>
          Right(storedShape.copy(sides = storedShape.sides + 1))
      }
      .left
      .value

    err shouldBe a[UpdateNoSourceError]
  }

  it("adds the S3 location to the result of upsert()") {
    val store = createSourceVHS[Shape]

    // Call upsert() on an empty store, which should cause this item
    // to be initialised at v1.
    val result1 = store
      .upsert("splodge")(Shape(sides = 11, colour = "yellow")) {
        storedShape =>
          Right(storedShape.copy(sides = storedShape.sides + 1))
      }
      .value

    result1.id shouldBe Version("splodge", version = 0)
    val (location1, storedShape1) = result1.identifiedT

    storedShape1 shouldBe Shape(sides = 11, colour = "yellow")
    store.underlying.hybridStore.typedStore
      .get(location1)
      .value
      .identifiedT shouldBe Shape(sides = 11, colour = "yellow")

    // Now call upsert() a second time, which will update the entry
    // in the store.
    val result2 = store
      .upsert("splodge")(Shape(sides = 11, colour = "yellow")) {
        storedShape =>
          Right(storedShape.copy(sides = storedShape.sides + 1))
      }
      .value

    result2.id shouldBe Version("splodge", version = 1)
    val (location2, storedShape2) = result2.identifiedT

    storedShape2 shouldBe Shape(sides = 12, colour = "yellow")
    store.underlying.hybridStore.typedStore
      .get(location2)
      .value
      .identifiedT shouldBe Shape(sides = 12, colour = "yellow")
  }
}
