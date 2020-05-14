package uk.ac.wellcome.sierra_adapter.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.store.VersionedStore

object SierraTransformableVHSBuilder {

  def buildSierraVHS(config: Config): VersionedStore[String, Int, SierraTransformable] = {

    new VersionedStore(
      new VHSWrapper(
        VHSBuilder.build[SierraTransformable](config)
      )
    )
  }
}
