package weco.pipeline.mets_adapter.services

import weco.catalogue.source_model.mets.MetsSourceData
import weco.storage.store.VersionedStore
import weco.storage.{Identified, Version}

class MetsStore(val store: VersionedStore[String, Int, MetsSourceData]) {
  def storeData(key: Version[String, Int], data: MetsSourceData)
    : Either[Throwable, Identified[Version[String, Int], MetsSourceData]] =
    store
      .put(key)(data)
      .left.map(_.e)
}
