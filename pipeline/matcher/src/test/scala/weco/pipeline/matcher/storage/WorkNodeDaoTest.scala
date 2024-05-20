package weco.pipeline.matcher.storage

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkNodeGenerators
import weco.pipeline.matcher.models.{SourceWorkData, SubgraphId, WorkNode}
import weco.storage.dynamo.DynamoConfig

import javax.naming.ConfigurationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

class WorkNodeDaoTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with MatcherFixtures
    with WorkNodeGenerators {

  describe("Get from dynamo") {
    it("returns nothing if ids are not in dynamo") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              whenReady(workNodeDao.get(Set(createCanonicalId))) {
                _ shouldBe Set.empty
              }
          }
      }
    }

    it("returns WorkNodes which are stored in DynamoDB") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              val (workA, workB) = createTwoWorks("A->B")

              putTableItems(items = Seq(workA, workB), table = table)

              whenReady(workNodeDao.get(Set(idA, idB))) {
                _ shouldBe Set(workA, workB)
              }
          }
      }
    }

    it("returns an error if fetching from dynamo fails") {
      val matcherGraphDao = new WorkNodeDao(
        dynamoClient,
        dynamoConfig = createDynamoConfigWith(nonExistentTable)
      )

      whenReady(matcherGraphDao.get(Set(idA)).failed) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }
  }

  describe("getBySubgraphIds") {
    it("returns empty set if the subgraph ID isn't being used") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              whenReady(workNodeDao.getBySubgraphIds(Set("Not-there"))) {
                _ shouldBe Set()
              }
          }
      }
    }

    it("finds the matching works") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            matcherGraphDao =>
              val (workA, workB) = createTwoWorks("A->B")

              putTableItems(items = Seq(workA, workB), table = table)

              whenReady(
                matcherGraphDao.getBySubgraphIds(Set(SubgraphId(idA, idB)))
              ) {
                _ shouldBe Set(workA, workB)
              }
          }
      }
    }

    it("fails if fetching from DynamoDB fails") {
      val workNodeDao = new WorkNodeDao(
        dynamoClient,
        dynamoConfig = createDynamoConfigWith(nonExistentTable)
      )

      whenReady(
        workNodeDao.getBySubgraphIds(Set(SubgraphId(idA, idB))).failed
      ) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }

    it("fails if Scanamo can't deserialise the data in the table") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              case class BadRecord(
                id: CanonicalId,
                subgraphId: String,
                componentIds: Int
              )
              val badRecord: BadRecord =
                BadRecord(
                  id = idA,
                  subgraphId = SubgraphId(idA, idB),
                  componentIds = 123
                )

              putTableItem(badRecord, table = table)

              val future =
                workNodeDao.getBySubgraphIds(setIds = Set(SubgraphId(idA, idB)))

              whenReady(future.failed) {
                _ shouldBe a[RuntimeException]
              }
          }
      }
    }
  }

  describe("Insert into dynamo") {
    it("puts a WorkNode") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              val work = createOneWork("A")

              whenReady(workNodeDao.put(Set(work))) {
                _ =>
                  getTableItem[WorkNode](
                    work.id.underlying,
                    table
                  ) shouldBe Some(Right(work))
              }
          }
      }
    }

    it("puts lots of WorkNodes (>25 = a single BatchPutItem)") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              val works = (1 to 50).map {
                _ =>
                  val id = createCanonicalId
                  WorkNode(
                    id = id,
                    subgraphId = SubgraphId(id),
                    componentIds = List(id),
                    sourceWork =
                      SourceWorkData(id = createSourceIdentifier, version = 1)
                  )
              }

              val future = workNodeDao.put(works.toSet)

              whenReady(future) {
                _ =>
                  val storedWorks = scanTable[WorkNode](table).collect {
                    case Right(w) => w
                  }

                  storedWorks should contain theSameElementsAs works
              }
          }
      }
    }

    it("returns an error if Scanamo fails to put a record") {
      withWorkGraphTable {
        table =>
          withWorkNodeDao(table) {
            workNodeDao =>
              case class BadRecord(id: CanonicalId, componentIds: String)
              val badRecord: BadRecord =
                BadRecord(id = idA, componentIds = "1, 2, 3")

              putTableItem(badRecord, table = table)

              whenReady(workNodeDao.get(ids = Set(idA)).failed) {
                _ shouldBe a[RuntimeException]
              }
          }
      }
    }

    it("returns an error if put to dynamo fails") {
      val workNodeDao = new WorkNodeDao(
        dynamoClient,
        dynamoConfig = createDynamoConfigWith(nonExistentTable)
      )

      val work = createOneWork("A")

      whenReady(workNodeDao.put(Set(work)).failed) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }

    it("cannot be instantiated if dynamoConfig.maybeIndex is None") {
      intercept[ConfigurationException] {
        new WorkNodeDao(
          dynamoClient,
          DynamoConfig("something", maybeIndexName = None)
        )
      }
    }
  }
}
