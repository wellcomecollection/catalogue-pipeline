package uk.ac.wellcome.mets_adapter.services

import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, StorageError, NoVersionExistsError}

/** Stores METS data only if a newer version and the METS XML location is pointing
 *  to a different file, returning the stored data if it has been updated.
 */
class MetsStore(store: VersionedStore[String, Int, MetsData]) {

  def storeMetsData(bagId: String, data: MetsData): Either[Throwable, Option[MetsData]] =
    shouldUpdate(bagId, data)
      .right.flatMap {
        case true => store.putLatest(bagId)(data).right.map(_ => Some(data))
        case false => Right(None)
      }
      .left.map(_.e)

  def shouldUpdate(bagId: String, data: MetsData): Either[StorageError, Boolean] =
    store.getLatest(bagId) match {
      case Right(Identified(_, existingData)) => 
        Right(existingData.version >= data.version && existingData.path != data.path)
      case Left(_: NoVersionExistsError) => Right(true)
      case Left(err) => Left(err)
    }
}
