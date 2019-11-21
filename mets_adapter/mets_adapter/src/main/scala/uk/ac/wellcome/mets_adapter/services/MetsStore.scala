package uk.ac.wellcome.mets_adapter.services

import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, NoVersionExistsError}

/** Stores METS data. The data should first be filtered to ensure storing is
  *  performed only if it is a newer version and the location is pointing to a
  *  different XML file.
  */
class MetsStore(store: VersionedStore[String, Int, MetsData]) {

  def storeMetsData(bagId: String,
                    data: MetsData): Either[Throwable, MetsData] =
    store
      .putLatest(bagId)(data)
      .right
      .map(_ => data)
      .left
      .map(_.e)

  def filterMetsData(bagId: String,
                     data: MetsData): Either[Throwable, Option[MetsData]] =
    store.getLatest(bagId) match {
      case Right(Identified(_, existingData)) =>
        Right(
          if (shouldUpdate(data, existingData)) Some(data) else None
        )
      case Left(_: NoVersionExistsError) => Right(Some(data))
      case Left(err)                     => Left(err.e)
    }

  private def shouldUpdate(newData: MetsData, existingData: MetsData) =
    newData.version >= existingData.version
}
