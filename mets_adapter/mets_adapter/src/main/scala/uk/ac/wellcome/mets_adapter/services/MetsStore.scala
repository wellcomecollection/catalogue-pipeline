package uk.ac.wellcome.mets_adapter.services

import grizzled.slf4j.Logging
import weco.storage.VersionAlreadyExistsError
import weco.storage.store.VersionedStore
import weco.storage.{Identified, Version}
import weco.catalogue.source_model.mets.MetsSourceData

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
