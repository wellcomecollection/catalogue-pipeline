package weco.catalogue.source_model.store

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryTypedStore}
import uk.ac.wellcome.storage.store.{HybridStoreWithMaxima, Store, TypedStore, VersionedHybridStore}
import uk.ac.wellcome.storage.{UpdateNoSourceError, Version}

class SourceVHSTest extends AnyFunSpec with Matchers with EitherValues with S3ObjectLocationGenerators {
  case class Shape(sides: Int, colour: String)

  it("adds the S3 location to the result of putLatest()") {
    val store = new SourceVHS[Shape](createStore)

    val shape = Shape(sides = 4, colour = "red")

    val result = store.putLatest(id = "redsquare")(shape).value

    result.id shouldBe Version("redsquare", version = 0)
    val (location, storedShape) = result.identifiedT

    storedShape shouldBe shape
    store.underlying.hybridStore.typedStore.get(location).value.identifiedT shouldBe shape
  }

  it("adds the S3 location to the result of update()") {
    val store = new SourceVHS[Shape](createStore)

    val shape = Shape(sides = 4, colour = "blue")
    store.putLatest(id = "bluething")(shape) shouldBe a[Right[_, _]]

    val result = store.update("bluething") { storedShape =>
      Right(storedShape.copy(sides = storedShape.sides + 1))
    }.value

    result.id shouldBe Version("bluething", version = 1)
    val (location, storedShape) = result.identifiedT

    storedShape shouldBe Shape(sides = 5, colour = "blue")
    store.underlying.hybridStore.typedStore.get(location).value.identifiedT shouldBe Shape(sides = 5, colour = "blue")
  }

  it("wraps an error from an update()") {
    val store = new SourceVHS[Shape](createStore)

    val err = store.update("doesNotExist") { storedShape =>
      Right(storedShape.copy(sides = storedShape.sides + 1))
    }.left.value

    err shouldBe a[UpdateNoSourceError]
  }

  it("adds the S3 location to the result of upsert()") {
    val store = new SourceVHS[Shape](createStore)

    // Call upsert() on an empty store, which should cause this item
    // to be initialised at v1.
    val result1 = store
      .upsert("splodge")(
        Shape(sides = 11, colour = "yellow")) { storedShape =>
          Right(storedShape.copy(sides = storedShape.sides + 1))
        }
      .value

    result1.id shouldBe Version("splodge", version = 0)
    val (location1, storedShape1) = result1.identifiedT

    storedShape1 shouldBe Shape(sides = 11, colour = "yellow")
    store.underlying.hybridStore.typedStore.get(location1).value.identifiedT shouldBe Shape(sides = 11, colour = "yellow")

    // Now call upsert() a second time, which will update the entry
    // in the store.
    val result2 = store
      .upsert("splodge")(
        Shape(sides = 11, colour = "yellow")) { storedShape =>
        Right(storedShape.copy(sides = storedShape.sides + 1))
      }
      .value

    result2.id shouldBe Version("splodge", version = 1)
    val (location2, storedShape2) = result2.identifiedT

    storedShape2 shouldBe Shape(sides = 12, colour = "yellow")
    store.underlying.hybridStore.typedStore.get(location2).value.identifiedT shouldBe Shape(sides = 12, colour = "yellow")
  }

  def createStore: VersionedHybridStore[String, Int, S3ObjectLocation, Shape] = {
    val hybridStore = new HybridStoreWithMaxima[String, Int, S3ObjectLocation, Shape] {
      implicit override val indexedStore: Store[Version[String, Int], S3ObjectLocation] with Maxima[String, Version[String, Int], S3ObjectLocation] =
        new MemoryStore[Version[String, Int], S3ObjectLocation](initialEntries = Map.empty)
          with MemoryMaxima[String, S3ObjectLocation]

      override implicit val typedStore: TypedStore[S3ObjectLocation, Shape] =
        MemoryTypedStore[S3ObjectLocation, Shape]()

      override protected def createTypeStoreId(id: Version[String, Int]): S3ObjectLocation =
        createS3ObjectLocation
    }

    new VersionedHybridStore(hybridStore)
  }
}
