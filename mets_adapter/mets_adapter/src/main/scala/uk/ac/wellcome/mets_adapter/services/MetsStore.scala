package uk.ac.wellcome.mets_adapter.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.storage.VersionAlreadyExistsError
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.mets_adapter.models.MetsData

class MetsStore(store: VersionedStore[String, Int, MetsData])
    extends Logging {

  def storeData(key: Version[String, Int],
                data: MetsData): Either[Throwable, Version[String, Int]] =
    store
      .put(key)(data)
      .map { case Identified(key, _) => key }
      .left.flatMap {
        case VersionAlreadyExistsError(_) =>
          warn(s"$key already exists in store so re-publishing")
          Right(key)
        case err => Left(err.e)
      }
}
