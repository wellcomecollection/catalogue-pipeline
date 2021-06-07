package weco.catalogue.tei.id_extractor

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scalikejdbc._
import weco.catalogue.tei.id_extractor.fixtures.PathIdDatabase

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits._

class PathIdDaoTest extends AnyFunSpec with ScalaFutures with Matchers with PathIdDatabase {
  describe("save"){
    it("saves a path and a timestamp in the database") {
      val time = ZonedDateTime.parse("2021-05-27T16:05:00Z")
      withPathIdDao(initializeTable = true) { case (_,table, pathIdDao) =>
        implicit val session = AutoSession

        val path = "Arabic/WMS_Arabic_1.xml"
        val id = "abcd"

        val future = pathIdDao.save(id, path, time)

        whenReady(future) { _ =>

          val maybePathId = withSQL {
            select.from(table as table.p)
          }.map(PathId(table.p)).single.apply()

          maybePathId shouldBe Some(PathId(path, id, time))
        }

      }
    }
  }

  describe("getByPath"){
    it("retrieves the path"){
      val time = ZonedDateTime.parse("2021-05-27T16:05:00Z")
      withPathIdDao(initializeTable = true) { case (_,table, pathIdDao) =>

        implicit val session = AutoSession

        val path = "Arabic/WMS_Arabic_1.xml"
        val id = "abcd"
        withSQL{
          insert.into(table).values(path, id, time.format(PathId.formatter))
        }.update().apply()
        val future = pathIdDao.getByPath( path)

        whenReady(future) { result =>
          result shouldBe Some(PathId(path, id, time))
        }
    }
    }
}}
