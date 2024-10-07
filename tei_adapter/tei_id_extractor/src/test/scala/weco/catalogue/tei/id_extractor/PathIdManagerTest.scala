package weco.catalogue.tei.id_extractor

import org.scalatest.funspec.AnyFunSpec
import scalikejdbc._
import weco.fixtures.TestWith
import weco.messaging.memory.MemoryMessageSender
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryStore
import weco.catalogue.tei.id_extractor.PathIdManager.insertPathId
import weco.catalogue.tei.id_extractor.database.PathIdTable
import weco.catalogue.tei.id_extractor.fixtures.PathIdDatabase
import weco.catalogue.tei.id_extractor.models.PathId
import weco.catalogue.source_model.tei.{
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage
}
import weco.catalogue.source_model.Implicits._

import java.time.Instant
import java.time.temporal.ChronoUnit

class PathIdManagerTest extends AnyFunSpec with PathIdDatabase {
  val bucket = Bucket("bucket")
  val blobContents = "blah"
  describe("handlePathChanged") {
    it("stores a previously unseen path & id") {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession
          val pathId = PathId(
            "Batak/WMS_Batak_1.xml",
            "manuscript_1234",
            Instant.parse("2021-06-07T10:00:00Z")
          )

          manager.handlePathChanged(pathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(pathId)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            "2021-06-07T10:00:00Z",
            blobContents,
            pathId.id
          )

          messageSender
            .getMessages[
              TeiIdChangeMessage
            ]() should contain only (TeiIdChangeMessage(
            id = pathId.id,
            s3Location = expectedS3Location,
            Instant.parse("2021-06-07T10:00:00Z")
          ))

      }
    }

    it("records a change to previously seen id & path") {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession

          val oldTime = Instant.parse("2021-06-07T10:00:00Z")
          val storedPathId =
            PathId("Batak/path.xml", "manuscript_1234", oldTime)
          insertPathId(table, storedPathId).get
          val updatedPathId =
            storedPathId.copy(timeModified = oldTime.plus(2, ChronoUnit.HOURS))

          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(updatedPathId)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            "2021-06-07T12:00:00Z",
            blobContents,
            updatedPathId.id
          )

          messageSender
            .getMessages[
              TeiIdChangeMessage
            ]() should contain only (TeiIdChangeMessage(
            id = updatedPathId.id,
            s3Location = expectedS3Location,
            oldTime.plus(2, ChronoUnit.HOURS)
          ))
      }

    }

    it(
      "ignores a change to previously seen id & path if the timestamp saved is greater than the timestamp in the message"
    ) {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession

          val storedTime = Instant.parse("2021-06-07T10:00:00Z")
          val messageTime = storedTime.minus(2, ChronoUnit.HOURS)
          val storedPathId =
            PathId("Batak/path.xml", "manuscript_1234", storedTime)
          insertPathId(table, storedPathId).get
          val updatedPathId = storedPathId.copy(timeModified = messageTime)

          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(storedPathId)

          messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty
          store.entries.keySet shouldBe empty

      }

    }

    it("record that a previously seen id has moved") {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession
          val oldTime = Instant.parse("2021-06-07T10:00:00Z")
          val newTime = oldTime.plus(2, ChronoUnit.HOURS)

          val storedPathId =
            PathId("Batak/oldpath.xml", "manuscript_1234", oldTime)
          insertPathId(table, storedPathId).get

          val updatedPathId = storedPathId.copy(
            timeModified = newTime,
            path = "Batak/newpath.xml"
          )

          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(updatedPathId)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            "2021-06-07T12:00:00Z",
            blobContents,
            updatedPathId.id
          )

          messageSender
            .getMessages[
              TeiIdChangeMessage
            ]() should contain only TeiIdChangeMessage(
            id = updatedPathId.id,
            s3Location = expectedS3Location,
            newTime
          )
      }

    }

    it("records that a new id has moved into a previously seen path") {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession

          val newId = "manuscript_5678"
          val oldTime = Instant.parse("2021-06-07T10:00:00Z")
          val newTime = oldTime.plus(2, ChronoUnit.HOURS)
          val storedPathId =
            PathId("Batak/path.xml", "manuscript_1234", oldTime)
          insertPathId(table, storedPathId).get

          val updatedPathId =
            storedPathId.copy(id = newId, timeModified = newTime)

          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()
          maybePathId shouldBe Some(updatedPathId)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            "2021-06-07T12:00:00Z",
            blobContents,
            updatedPathId.id
          )

          messageSender
            .getMessages[
              TeiIdMessage
            ]() should contain only (
            TeiIdChangeMessage(
              id = updatedPathId.id,
              s3Location = expectedS3Location,
              newTime
            ),
            TeiIdDeletedMessage(id = "manuscript_1234", newTime)
          )

      }
    }

    it(
      "ignores messages for a previously seen path if stored time is newer than the message time"
    ) {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession
          val storedTime = Instant.parse("2021-06-07T10:00:00Z")
          val newTime = storedTime.minus(2, ChronoUnit.HOURS)
          val storedPathId =
            PathId("Batak/oldpath.xml", "manuscript_1234", storedTime)
          insertPathId(table, storedPathId).get

          val updatedPathId =
            storedPathId.copy(id = "manuscript_5678", timeModified = newTime)
          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(storedPathId)
          messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty
          store.entries.keySet shouldBe empty
      }

    }

    it(
      "ignores changes to a saved id if the timeModified in the database is greater"
    ) {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession

          val savedTime = Instant.parse("2021-06-07T10:00:00Z")
          val newTime = savedTime.minus(2, ChronoUnit.HOURS)
          val storedPathId =
            PathId("Batak/oldpath.xml", "manuscript_1234", savedTime)
          insertPathId(table, storedPathId).get

          val updatedPathId = storedPathId.copy(
            path = "Batak/newpath.xml",
            timeModified = newTime
          )
          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(storedPathId)
          store.entries.keySet shouldBe empty
          messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty

      }
    }

    it(
      "records that a previously seen id has moved into a previously seen path"
    ) {

      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession

          val path1 = "Batak/path.xml"
          val id1 = "manuscript_1234"
          val time1 = Instant.parse("2021-06-07T10:00:00Z")

          val path2 = "Batak/anotherpath.xml"
          val id2 = "manuscript_5678"
          val time2 = time1.plus(2, ChronoUnit.HOURS)
          val firstPathId = PathId(path1, id1, time1)
          val secondPathId = PathId(path2, id2, time2)
          insertPathId(table, firstPathId).get
          insertPathId(table, secondPathId).get

          val newTime = time2.plus(2, ChronoUnit.HOURS)
          val updatedPathId = PathId(path1, id2, newTime)

          manager.handlePathChanged(updatedPathId, blobContents)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()
          maybePathId shouldBe Some(updatedPathId)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            "2021-06-07T14:00:00Z",
            blobContents,
            id2
          )

          messageSender
            .getMessages[
              TeiIdMessage
            ]() should contain only (
            TeiIdChangeMessage(
              id = id2,
              s3Location = expectedS3Location,
              newTime
            ),
            TeiIdDeletedMessage(id = id1, newTime)
          )

      }
    }
    it(
      "ignores changes to previously seen id and  previously seen path if the timestamp in the message is older than the ones in the database"
    ) {
      withPathIdManager(bucket) {
        case (table, manager, store, messageSender) =>
          implicit val session = AutoSession

          val path1 = "Batak/path.xml"
          val id1 = "manuscript_1234"
          val time1 = Instant.parse("2021-06-07T10:00:00Z")

          val path2 = "Batak/anotherpath.xml"
          val id2 = "manuscript_5678"
          val time2 = time1.plus(2, ChronoUnit.HOURS)
          val firstPathId = PathId(path1, id1, time1)
          val secondPathId = PathId(path2, id2, time2)
          insertPathId(table, firstPathId).get
          insertPathId(table, secondPathId).get

          val newTime = time1.minus(2, ChronoUnit.HOURS)

          manager.handlePathChanged(PathId(path1, id2, newTime), blobContents)

          val listOfpathIds = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).list().apply()

          listOfpathIds should contain theSameElementsAs List(
            firstPathId,
            secondPathId
          )
          store.entries shouldBe empty
          messageSender.getMessages[TeiIdMessage]() shouldBe empty
      }
    }
  }

  describe("handlePathDeleted") {
    it("deletes a path") {
      withPathIdManager(bucket) {
        case (table, manager, _, messageSender) =>
          implicit val session = AutoSession

          val path = "Batak/WMS_Batak_1.xml"
          val id = "manuscript_1234"
          val time = Instant.parse("2021-06-07T10:00:00Z")
          insertPathId(table, PathId(path, id, time)).get

          val newTime = time.plus(2, ChronoUnit.HOURS)

          manager.handlePathDeleted(path, newTime)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()
          maybePathId shouldBe None
          messageSender
            .getMessages[
              TeiIdDeletedMessage
            ]() should contain only TeiIdDeletedMessage(
            id = "manuscript_1234",
            newTime
          )
      }
    }

    it(
      "does not delete a path if the timeModified in the table is greater than the timeDeleted"
    ) {
      withPathIdManager(bucket) {
        case (table, manager, _, messageSender) =>
          implicit val session = AutoSession
          val path = "Batak/WMS_Batak_1.xml"
          val id = "manuscript_1234"
          val time = Instant.parse("2021-06-07T10:00:00Z")
          insertPathId(table, PathId(path, id, time)).get

          val deletedTime = time.minus(2, ChronoUnit.HOURS)

          manager.handlePathDeleted(path, deletedTime)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(PathId(path, id, time))
          messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty
      }
    }

    it("ignores if the pathId does not exist") {
      withPathIdManager(bucket) {
        case (table, manager, _, messageSender) =>
          implicit val session = AutoSession
          val path = "Batak/WMS_Batak_1.xml"
          val time = Instant.parse("2021-06-07T10:00:00Z")

          val deletedTime = time.plus(2, ChronoUnit.HOURS)
          manager.handlePathDeleted(path, deletedTime)

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe None
          messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty
      }
    }
  }

  private def checkFileIsStored(
    store: MemoryStore[S3ObjectLocation, String],
    bucket: Bucket,
    modifiedTime: String,
    fileContents: String,
    id: String
  ) = {
    val expectedKey =
      s"tei_files/${id}/${Instant.parse(modifiedTime).getEpochSecond}.xml"
    val expectedS3Location = S3ObjectLocation(bucket.name, expectedKey)
    store.entries.keySet should contain(expectedS3Location)
    store.entries(expectedS3Location) shouldBe fileContents
    expectedS3Location
  }

  def withPathIdManager[R](bucket: Bucket)(
    testWith: TestWith[
      (
        PathIdTable,
        PathIdManager[String],
        MemoryStore[S3ObjectLocation, String],
        MemoryMessageSender
      ),
      R
    ]
  ) =
    withInitializedPathIdTable {
      table =>
        val store = new MemoryStore[S3ObjectLocation, String](Map())
        val messageSender: MemoryMessageSender = new MemoryMessageSender()
        val manager =
          new PathIdManager(table, store, messageSender, bucket.name)
        testWith((table, manager, store, messageSender))
    }
}
