package weco.catalogue.sierra_linker.services

import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, UpdateNotApplied}
import weco.catalogue.sierra_adapter.models.{
  AbstractSierraRecord,
  TypedSierraRecordNumber
}
import weco.catalogue.sierra_linker.models.{Link, LinkOps}

class LinkStore[Id <: TypedSierraRecordNumber,
                Record <: AbstractSierraRecord[Id]](
  store: VersionedStore[Id, Int, Link]
)(
  implicit linkOps: LinkOps[Record]
) {
  def update(newRecord: Record): Either[Throwable, Option[Record]] = {
    val newLink = linkOps.createLink(newRecord)

    val upsertResult: store.UpdateEither =
      store.upsert(newRecord.id)(newLink) {
        linkOps.updateLink(_, newRecord) match {
          case Some(updatedLink) => Right(updatedLink)
          case None =>
            Left(
              UpdateNotApplied(
                new Throwable(s"Item ${newRecord.id} is already up-to-date")))
        }
      }

    upsertResult match {
      case Right(Identified(_, updatedLink)) =>
        Right(
          Some(
            linkOps.copyUnlinkedBibIds(updatedLink, newRecord)
          ))

      case Left(_: UpdateNotApplied) => Right(None)
      case Left(err)                 => Left(err.e)
    }
  }
}
