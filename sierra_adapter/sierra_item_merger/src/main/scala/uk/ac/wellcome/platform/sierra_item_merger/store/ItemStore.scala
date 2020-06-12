package uk.ac.wellcome.platform.sierra_item_merger.store

import grizzled.slf4j.Logging
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}

class ItemStore(itemRecordStore: VersionedStore[String, Int, SierraItemRecord]) extends Logging{
  def get(key: Version[String, Int]): Either[Throwable, SierraItemRecord] = itemRecordStore
    .getLatest(key.id)  match {
    case Left(err) =>
      error(s"Error fetching $key from VHS")
      Left(err.e)
    case Right(Identified(id, entry)) if id.version == key.version =>
      Right(entry)
    case Right(Identified(id, _)) if id.version > key.version =>
      // If the same message gets delivered to the items_to_dynamo twice in quick succession,
      // the version in the items VHS advances. This is an expected case
      // where we can simply ignore the current message. To do this, we send a
      // [[VersionExpectedConflictException]] downstream so the message can be deleted.
      Left(VersionExpectedConflictException())
    case Right(Identified(id, _)) =>
      Left(new Exception(
        s"Version in vhs ${id.version} is lower than requested version ${key.version} for id $id"))
  }
}

case class VersionExpectedConflictException() extends Exception
