package weco.pipeline.matcher.workgraph

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.models.{
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkGraph,
  WorkNode,
  WorkStub
}

class WorkGraphUpdaterTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures {

  val idA = CanonicalId("AAAAAAAA")
  val idB = CanonicalId("BBBBBBBB")
  val idC = CanonicalId("CCCCCCCC")
  val idD = CanonicalId("DDDDDDDD")

  describe("Adding links without existing works") {
    it("updating nothing with A gives A:A") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idA,
            modifiedTime = modifiedTime1,
            referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime1,
          linkedIds = List(),
          componentId = ciHash(idA)))
    }

    it("updating nothing with A->B gives A+B:A->B") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idA,
            modifiedTime = modifiedTime1,
            referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime1,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idB,
          modifiedTime = None,
          linkedIds = List(),
          componentId = ciHash(idA, idB))
      )
    }

    it("updating nothing with B->A gives A+B:B->A") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idB,
            modifiedTime = modifiedTime1,
            referencedWorkIds = Set(idA)),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode(
          idB,
          modifiedTime = modifiedTime1,
          linkedIds = List(idA),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idA,
          modifiedTime = None,
          linkedIds = List(),
          componentId = ciHash(idA, idB))
      )
    }
  }

  describe("Adding links to existing works") {
    it("updating A, B with A->B gives A+B:(A->B, B)") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idA,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                modifiedTime = modifiedTime1,
                linkedIds = Nil,
                componentId = ciHash(idA)),
              WorkNode(
                idB,
                modifiedTime = modifiedTime1,
                linkedIds = Nil,
                componentId = ciHash(idB))
            )
          )
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode(
            idA,
            modifiedTime = modifiedTime2,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB)),
          WorkNode(
            idB,
            modifiedTime = modifiedTime1,
            linkedIds = List(),
            componentId = ciHash(idA, idB))
        )
    }

    it("updating A->B with A->B gives A+B:(A->B, B)") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idA,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime1,
              linkedIds = List(idB),
              componentId = ciHash(idA, idB)),
            WorkNode(
              idB,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = ciHash(idA, idB))
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idB,
          modifiedTime = modifiedTime1,
          linkedIds = List(),
          componentId = ciHash(idA, idB))
      )
    }

    it("updating A->B, B, C with B->C gives A+B+C:(A->B, B->C, C)") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idB,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set(idC)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime2,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = ciHash(idA, idB)),
            WorkNode(
              idC,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = ciHash(idC))
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idB,
          modifiedTime = modifiedTime2,
          linkedIds = List(idC),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idC,
          modifiedTime = modifiedTime1,
          linkedIds = List(),
          componentId = ciHash(idA, idB, idC))
      )
    }

    it("updating A->B, C->D with B->C gives A+B+C+D:(A->B, B->C, C->D, D)") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idB,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set(idC)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime1,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idC,
              modifiedTime = modifiedTime1,
              linkedIds = List(idD),
              componentId = "C+D"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = "A+B"),
            WorkNode(
              idD,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = "C+D")
          ))
        )
        .nodes shouldBe
        Set(
          WorkNode(
            idA,
            modifiedTime = modifiedTime1,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idB,
            modifiedTime = modifiedTime2,
            linkedIds = List(idC),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idC,
            modifiedTime = modifiedTime1,
            linkedIds = List(idD),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idD,
            modifiedTime = modifiedTime1,
            linkedIds = List(),
            componentId = ciHash(idA, idB, idC, idD))
        )
    }

    it("updating A->B with B->[C,D] gives A+B+C+D:(A->B, B->C&D, C, D") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idB,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set(idC, idD)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime2,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = ciHash(idA, idB)),
            WorkNode(
              idC,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = ciHash(idC)),
            WorkNode(
              idD,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = ciHash(idD))
          ))
        )
        .nodes shouldBe
        Set(
          WorkNode(
            idA,
            modifiedTime = modifiedTime2,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idB,
            modifiedTime = modifiedTime2,
            linkedIds = List(idC, idD),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idC,
            modifiedTime = modifiedTime1,
            linkedIds = List(),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idD,
            modifiedTime = modifiedTime1,
            linkedIds = List(),
            componentId = ciHash(idA, idB, idC, idD))
        )
    }

    it("updating A->B->C with A->C gives A+B+C:(A->B, B->C, C->A") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idC,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set(idA)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime2,
              linkedIds = List(idB),
              componentId = "A+B+C"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime2,
              linkedIds = List(idC),
              componentId = "A+B+C"),
            WorkNode(
              idC,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = "A+B+C")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idB,
          modifiedTime = modifiedTime2,
          linkedIds = List(idC),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idC,
          modifiedTime = modifiedTime2,
          linkedIds = List(idA),
          componentId = ciHash(idA, idB, idC))
      )
    }
  }

  describe("Update version") {
    it("processes an update for a newer version") {
      val existingModifiedTime = modifiedTime1
      val updateModifiedTime = modifiedTime2
      WorkGraphUpdater
        .update(
          work = WorkStub(idA, updateModifiedTime, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                existingModifiedTime,
                linkedIds = Nil,
                componentId = ciHash(idA))))
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode(
            idA,
            modifiedTime = updateModifiedTime,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB)),
          WorkNode(
            idB,
            modifiedTime = None,
            linkedIds = List(),
            componentId = ciHash(idA, idB))
        )
    }

    it("doesn't process an update for a lower version") {
      val existingModifiedTime = modifiedTime3
      val updateModifiedTime = modifiedTime1

      val thrown = intercept[VersionExpectedConflictException] {
        WorkGraphUpdater
          .update(
            work =
              WorkStub(idA, updateModifiedTime, referencedWorkIds = Set(idB)),
            existingGraph = WorkGraph(
              Set(
                WorkNode(
                  idA,
                  existingModifiedTime,
                  linkedIds = Nil,
                  componentId = ciHash(idA))))
          )
      }
      thrown.message shouldBe s"update failed, work:$idA (modified 1970-01-01T00:00:01Z) is not newer than existing work (modified 1970-01-01T00:00:03Z)"
    }

    it(
      "processes an update for the same version if it's the same as the one stored") {
      val existingVersion = modifiedTime2
      val updateVersion = modifiedTime2

      WorkGraphUpdater
        .update(
          work = WorkStub(idA, updateVersion, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              existingVersion,
              linkedIds = List(idB),
              componentId = ciHash(idA, idB)),
            WorkNode(
              idB,
              modifiedTime = modifiedTime0,
              linkedIds = List(),
              componentId = ciHash(idA, idB))
          ))
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode(
            idA,
            updateVersion,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB)),
          WorkNode(
            idB,
            modifiedTime = modifiedTime0,
            linkedIds = List(),
            componentId = ciHash(idA, idB))
        )
    }

    it(
      "doesn't process an update for the same version if the work is different from the one stored") {
      val existingModifiedTime = modifiedTime2
      val updateModifiedTime = modifiedTime2

      val thrown = intercept[VersionUnexpectedConflictException] {
        WorkGraphUpdater
          .update(
            work =
              WorkStub(idA, updateModifiedTime, referencedWorkIds = Set(idA)),
            existingGraph = WorkGraph(
              Set(
                WorkNode(
                  idA,
                  existingModifiedTime,
                  linkedIds = List(idB),
                  componentId = ciHash(idA, idB)),
                WorkNode(
                  idB,
                  modifiedTime = modifiedTime0,
                  linkedIds = List(),
                  componentId = ciHash(idA, idB))
              ))
          )
      }
      thrown.getMessage shouldBe s"update failed, work:$idA (modified 1970-01-01T00:00:02Z) already exists with different content! update-ids:Set($idA) != existing-ids:Set($idB)"
    }
  }

  describe("Removing links") {
    it("updating  A->B with A gives A:A and B:B") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idA,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime1,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime1,
              linkedIds = List(),
              componentId = "A+B")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = List(),
          componentId = ciHash(idA)),
        WorkNode(
          idB,
          modifiedTime = modifiedTime1,
          linkedIds = List(),
          componentId = ciHash(idB))
      )
    }

    it("updating A->B with A but NO B (*should* not occur) gives A:A and B:B") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idA,
            modifiedTime = modifiedTime2,
            referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                modifiedTime = modifiedTime1,
                linkedIds = List(idB),
                componentId = "A+B")
            ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = Nil,
          componentId = ciHash(idA)),
        WorkNode(
          idB,
          modifiedTime = None,
          linkedIds = Nil,
          componentId = ciHash(idB))
      )
    }

    it("updating A->B->C with B gives A+B:(A->B, B) and C:C") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idB,
            modifiedTime = modifiedTime3,
            referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime2,
              linkedIds = List(idB),
              componentId = "A+B+C"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime2,
              linkedIds = List(idC),
              componentId = "A+B+C"),
            WorkNode(
              idC,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = "A+B+C")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idB,
          modifiedTime = modifiedTime3,
          linkedIds = Nil,
          componentId = ciHash(idA, idB)),
        WorkNode(
          idC,
          modifiedTime = modifiedTime1,
          linkedIds = Nil,
          componentId = ciHash(idC))
      )
    }

    it("updating A<->B->C with B->C gives A+B+C:(A->B, B->C, C)") {
      WorkGraphUpdater
        .update(
          work = WorkStub(
            idB,
            modifiedTime = modifiedTime3,
            referencedWorkIds = Set(idC)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              modifiedTime = modifiedTime2,
              linkedIds = List(idB),
              componentId = "A+B+C"),
            WorkNode(
              idB,
              modifiedTime = modifiedTime2,
              linkedIds = List(idA, idC),
              componentId = "A+B+C"),
            WorkNode(
              idC,
              modifiedTime = modifiedTime1,
              linkedIds = Nil,
              componentId = "A+B+C")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          modifiedTime = modifiedTime2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idB,
          modifiedTime = modifiedTime3,
          linkedIds = List(idC),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idC,
          modifiedTime = modifiedTime1,
          linkedIds = Nil,
          componentId = ciHash(idA, idB, idC))
      )
    }
  }
}
