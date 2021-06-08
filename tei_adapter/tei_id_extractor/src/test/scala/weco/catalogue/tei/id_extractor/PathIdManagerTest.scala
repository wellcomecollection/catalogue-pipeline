package weco.catalogue.tei.id_extractor

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import scalikejdbc._
import weco.catalogue.tei.id_extractor.fixtures.PathIdDatabase

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class PathIdManagerTest extends AnyFunSpec with PathIdDatabase with ScalaFutures{
  describe("handlePathChanged") {
    it("stores a previously unseen path & id"){
      withInitializedPathIdTable{ table =>
        implicit val session = AutoSession
        val manager = new PathIdManager(table)
        val path = "Batak/WMS_Batak_1.xml"
        val id = "manuscript_1234"
        val time = ZonedDateTime.parse("2021-06-07T10:00:00Z")
        whenReady(manager.handlePathChanged(path, id, time)){
          res =>
            res shouldBe Some(PathId(path, id, time))
            val maybePathId = withSQL {
              select.from(table as table.p)
            }.map(PathId(table.p)).single.apply()

            maybePathId shouldBe Some(res.get)
        }


      }
    }

    it("stores a new path for a previously seen id"){
      withInitializedPathIdTable{ table =>

      implicit val session = AutoSession


        val manager = new PathIdManager(table)
        val oldPath = "Batak/oldpath.xml"
        val newPath = "Batak/newpath.xml"
        val id = "manuscript_1234"
        val oldTime = ZonedDateTime.parse("2021-06-07T10:00:00Z")
        val newTime = oldTime.plus(2, ChronoUnit.HOURS)
        savePathId(PathId(oldPath, id, oldTime), table)

        val future = manager.handlePathChanged(newPath, id, newTime)
        whenReady(future){
          res =>
            res shouldBe Some(PathId(newPath, id, newTime))
            val maybePathId = withSQL {
              select.from(table as table.p)
            }.map(PathId(table.p)).single.apply()

            maybePathId shouldBe Some(res.get)
        }


      }
    }

    it("ignores changes to a saved id if the timeModified in the database is greater") {
      withInitializedPathIdTable{ table =>

      implicit val session = AutoSession


        val manager = new PathIdManager(table)
        val savedPath = "Batak/oldpath.xml"
        val newPath = "Batak/newpath.xml"
        val id = "manuscript_1234"
        val savedTime = ZonedDateTime.parse("2021-06-07T10:00:00Z")
        val newTime = savedTime.minus(2, ChronoUnit.HOURS)
        savePathId(PathId(savedPath, id, savedTime), table)

        val future = manager.handlePathChanged(newPath, id, newTime)
        whenReady(future){
          res =>
            res shouldBe None
            val maybePathId = withSQL {
              select.from(table as table.p)
            }.map(PathId(table.p)).single.apply()

            maybePathId shouldBe Some(PathId(savedPath, id, savedTime))
        }


      }
    }
  }

  describe("handlePathDeleted"){
    it("deletes a path"){
      withInitializedPathIdTable{ table =>

      implicit val session = AutoSession
        val manager = new PathIdManager(table)
        val path = "Batak/WMS_Batak_1.xml"
        val id = "manuscript_1234"
        val time = ZonedDateTime.parse("2021-06-07T10:00:00Z")
        savePathId(PathId(path, id, time), table)

        val newTime = time.plus(2, ChronoUnit.HOURS)
        whenReady(manager.handlePathDeleted(path, newTime)){
          res =>
            res shouldBe Some(PathId(path, id, newTime))
            val maybePathId = withSQL {
              select.from(table as table.p)
            }.map(PathId(table.p)).single.apply()

            maybePathId shouldBe None
        }


      }
    }

    it("does not delete a path if the timeModified in the table is greater than the timeDeleted"){
      withInitializedPathIdTable{ table =>

      implicit val session = AutoSession
        val manager = new PathIdManager(table)
        val path = "Batak/WMS_Batak_1.xml"
        val id = "manuscript_1234"
        val time = ZonedDateTime.parse("2021-06-07T10:00:00Z")
        savePathId(PathId(path, id, time), table)

        val deletedTime = time.minus(2, ChronoUnit.HOURS)
        whenReady(manager.handlePathDeleted(path, deletedTime)){
          res =>
            res shouldBe None
            val maybePathId = withSQL {
              select.from(table as table.p)
            }.map(PathId(table.p)).single.apply()

            maybePathId shouldBe Some(PathId(path, id, time))
        }


      }
    }

    it("errors if the pathId does not exist"){
      withInitializedPathIdTable{ table =>

      val manager = new PathIdManager(table)
        val path = "Batak/WMS_Batak_1.xml"
        val time = ZonedDateTime.parse("2021-06-07T10:00:00Z")

        val deletedTime = time.plus(2, ChronoUnit.HOURS)
        whenReady(manager.handlePathDeleted(path, deletedTime).failed){ exception =>
            exception shouldBe a[Exception]

        }


      }
    }
  }

  private def savePathId(pathId:PathId, pathIds: PathIdTable)(implicit session: DBSession) = withSQL {
    insert
      .into(pathIds)
      .namedValues(
        pathIds.column.path -> pathId.path,
        pathIds.column.id -> pathId.id,
        pathIds.column.timeModified -> pathId.timeModified.format( PathId.formatter)
      )
  }.update.apply()

}
