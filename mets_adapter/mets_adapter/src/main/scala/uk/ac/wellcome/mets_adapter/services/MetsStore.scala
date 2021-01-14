package uk.ac.wellcome.mets_adapter.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.storage.VersionAlreadyExistsError
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import weco.catalogue.source_model.mets.NewMetsSourceData

class MetsStore(val store: VersionedStore[String, Int, NewMetsSourceData])
    extends Logging {

  def storeData(key: Version[String, Int], data: NewMetsSourceData)
    : Either[Throwable, Identified[Version[String, Int], NewMetsSourceData]] =
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
