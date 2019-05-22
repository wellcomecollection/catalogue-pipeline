package uk.ac.wellcome.platform.matcher.storage

import com.gu.scanamo.Scanamo
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}

import scala.util.{Failure, Success}

class WorkGraphStoreTest
    extends FunSpec
    with Matchers
    with MockitoSugar
    with MatcherFixtures {

  describe("Get graph of linked works") {
    it("returns nothing if there are no matching graphs") {
      withWorkGraphStore { workGraphStore =>
        val result = workGraphStore.findAffectedWorks(
          WorkUpdate("Not-there", 0, Set.empty))

        result shouldBe Success(WorkGraph(Set.empty))
      }
    }

    it(
      "returns a WorkNode if it has no links and it's the only node in the setId") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val work =
            WorkNode(id = "A", version = 0, linkedIds = Nil, componentId = "A")
          Scanamo.put(dynamoDbClient)(graphTable.name)(work)

          val result = workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set.empty))
          result shouldBe Success(WorkGraph(Set(work)))
        }
      }
    }

    it("returns a WorkNode and the links in the workUpdate") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(id = "A", version = 0, linkedIds = Nil, componentId = "A")
          val workB =
            WorkNode(id = "B", version = 0, linkedIds = Nil, componentId = "B")
          Scanamo.put(dynamoDbClient)(graphTable.name)(workA)
          Scanamo.put(dynamoDbClient)(graphTable.name)(workB)

          val result = workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set("B")))
          result shouldBe Success(WorkGraph(Set(workA, workB)))
        }
      }
    }

    it("returns a WorkNode and the links in the database") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = "A",
              version = 0,
              linkedIds = List("B"),
              componentId = "AB")
          val workB =
            WorkNode(id = "B", version = 0, linkedIds = Nil, componentId = "AB")

          Scanamo.put(dynamoDbClient)(graphTable.name)(workA)
          Scanamo.put(dynamoDbClient)(graphTable.name)(workB)

          val result = workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set.empty))
          result shouldBe Success(WorkGraph(Set(workA, workB)))
        }
      }
    }

    it(
      "returns a WorkNode and the links in the database more than one level down") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = "A",
              version = 0,
              linkedIds = List("B"),
              componentId = "ABC")
          val workB =
            WorkNode(
              id = "B",
              version = 0,
              linkedIds = List("C"),
              componentId = "ABC")
          val workC = WorkNode(
            id = "C",
            version = 0,
            linkedIds = Nil,
            componentId = "ABC")

          Scanamo.put(dynamoDbClient)(graphTable.name)(workA)
          Scanamo.put(dynamoDbClient)(graphTable.name)(workB)
          Scanamo.put(dynamoDbClient)(graphTable.name)(workC)

          val result = workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set.empty))
          result shouldBe Success(WorkGraph(Set(workA, workB, workC)))
        }
      }
    }

    it(
      "returns a WorkNode and the links in the database where an update joins two sets of works") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = "A",
              version = 0,
              linkedIds = List("B"),
              componentId = "AB")
          val workB =
            WorkNode(id = "B", version = 0, linkedIds = Nil, componentId = "AB")
          val workC =
            WorkNode(id = "C", version = 0, linkedIds = Nil, componentId = "C")

          Scanamo.put(dynamoDbClient)(graphTable.name)(workA)
          Scanamo.put(dynamoDbClient)(graphTable.name)(workB)
          Scanamo.put(dynamoDbClient)(graphTable.name)(workC)

          val result = workGraphStore.findAffectedWorks(WorkUpdate("B", 0, Set("C")))
          result shouldBe Success(WorkGraph(Set(workA, workB, workC)))
        }
      }
    }
  }

  describe("Put graph of linked works") {
    it("puts a simple graph") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workNodeA = WorkNode("A", version = 0, List("B"), "A+B")
          val workNodeB = WorkNode("B", version = 0, Nil, "A+B")

          val result = workGraphStore.put(WorkGraph(Set(workNodeA, workNodeB)))
          result shouldBe a[Success[_]]

          val savedWorks = Scanamo
            .scan[WorkNode](dynamoDbClient)(graphTable.name)
            .map(_.right.get)

          savedWorks should contain theSameElementsAs Seq(workNodeA, workNodeB)
        }
      }
    }
  }

  it("throws a RuntimeException if workGraphStore fails to put") {
    val mockWorkNodeDao = mock[WorkNodeDao]
    val expectedException = new RuntimeException("FAILED")
    when(mockWorkNodeDao.put(any[WorkNode]))
      .thenReturn(Failure(expectedException))
    val workGraphStore = new WorkGraphStore(mockWorkNodeDao)

    val result = workGraphStore.put(WorkGraph(Set(WorkNode("A", version = 0, Nil, "A+B"))))
    result shouldBe Failure(expectedException)
  }
}
