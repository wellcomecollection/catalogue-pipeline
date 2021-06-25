package weco.pipeline.mets_adapter.services

import grizzled.slf4j.Logging
import weco.catalogue.source_model.mets.MetsSourceData
import weco.storage.store.VersionedStore
import weco.storage.{Identified, Version, VersionAlreadyExistsError}

class MetsStore(val store: VersionedStore[String, Int, MetsSourceData])
    extends Logging {

  def storeData(key: Version[String, Int], data: MetsSourceData)
    : Either[Throwable, Identified[Version[String, Int], MetsSourceData]] =
    store
      .put(key)(data)
      .left
      .flatMap {
        case VersionAlreadyExistsError(_) =>
          warn(s"$key already exists in store so re-publishing")
          Right(Identified(key, data))
        case err => Left(err.e)
      }
}
