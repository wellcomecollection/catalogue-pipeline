package uk.ac.wellcome.sierra_adapter.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.streaming.Codec

object SierraVHSBuilder {

  def buildSierraVHS[T](config: Config)(
    implicit codec: Codec[T]): VersionedStore[String, Int, T] = {

    new VersionedStore(
      new VHSWrapper(
        VHSBuilder.build[T](config)
      )
    )
  }
}
