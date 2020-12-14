package weco.catalogue.source_model.fixtures

import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.{HybridStoreWithMaxima, Store, TypedStore, VersionedHybridStore}
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryTypedStore}
import uk.ac.wellcome.storage.streaming.Codec
import weco.catalogue.source_model.store.SourceVHS

trait SourceVHSFixture extends S3ObjectLocationGenerators with Matchers {
  def createStore[T](implicit codec: Codec[T])
    : VersionedHybridStore[String, Int, S3ObjectLocation, T] = {
    val hybridStore =
      new HybridStoreWithMaxima[String, Int, S3ObjectLocation, T] {
        implicit override val indexedStore
          : Store[Version[String, Int], S3ObjectLocation] with Maxima[
            String,
            Version[String, Int],
            S3ObjectLocation] =
          new MemoryStore[Version[String, Int], S3ObjectLocation](
            initialEntries = Map.empty)
          with MemoryMaxima[String, S3ObjectLocation]

        override implicit val typedStore: TypedStore[S3ObjectLocation, T] =
          MemoryTypedStore[S3ObjectLocation, T]()

        override protected def createTypeStoreId(
          id: Version[String, Int]): S3ObjectLocation =
          createS3ObjectLocation
      }

    new VersionedHybridStore(hybridStore)
  }

  def createSourceVHS[T](implicit codec: Codec[T]): SourceVHS[T] =
    createSourceVHSWith[T](initialEntries = Map.empty)

  def createSourceVHSWith[T](
    initialEntries: Map[Version[String, Int], T]
  )(implicit codec: Codec[T]): SourceVHS[T] = {
    val vhs = new SourceVHS[T](createStore[T])

    initialEntries.foreach { case (id, t) =>
      vhs.underlying.put(id)(t) shouldBe a[Right[_, _]]
    }

    vhs
  }
}
