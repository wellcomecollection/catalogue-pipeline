package uk.ac.wellcome.platform.matcher.workgraph

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models._
import weco.catalogue.internal_model.identifiers.CanonicalId

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
          links = WorkLinks(idA, version = 1, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 1,
          linkedIds = List(),
          componentId = ciHash(idA)))
    }

    it("updating nothing with A->B gives A+B:A->B") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, version = 1, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 1,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idB,
          version = None,
          linkedIds = List(),
          componentId = ciHash(idA, idB)))
    }

    it("updating nothing with B->A gives A+B:B->A") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idB, version = 1, referencedWorkIds = Set(idA)),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode(
          idB,
          version = 1,
          linkedIds = List(idA),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idA,
          version = None,
          linkedIds = List(),
          componentId = ciHash(idA, idB)))
    }
  }

  describe("Adding links to existing works") {
    it("updating A, B with A->B gives A+B:(A->B, B)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, version = 2, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = Nil,
                componentId = ciHash(idA)),
              WorkNode(
                idB,
                version = 1,
                linkedIds = Nil,
                componentId = ciHash(idB))
            )
          )
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB)),
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(),
            componentId = ciHash(idA, idB)))
    }

    it("updating A->B with A->B gives A+B:(A->B, B)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, version = 2, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = List(idB),
                componentId = ciHash(idA, idB)),
              WorkNode(
                idB,
                version = 1,
                linkedIds = Nil,
                componentId = ciHash(idA, idB))))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idB,
          version = 1,
          linkedIds = List(),
          componentId = ciHash(idA, idB))
      )
    }

    it("updating A->B, B, C with B->C gives A+B+C:(A->B, B->C, C)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idB, version = 2, referencedWorkIds = Set(idC)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              version = 2,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idB,
              version = 1,
              linkedIds = Nil,
              componentId = ciHash(idA, idB)),
            WorkNode(
              idC,
              version = 1,
              linkedIds = Nil,
              componentId = ciHash(idC))
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idB,
          version = 2,
          linkedIds = List(idC),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idC,
          version = 1,
          linkedIds = List(),
          componentId = ciHash(idA, idB, idC))
      )
    }

    it("updating A->B, C->D with B->C gives A+B+C+D:(A->B, B->C, C->D, D)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idB, version = 2, referencedWorkIds = Set(idC)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              version = 1,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idC,
              version = 1,
              linkedIds = List(idD),
              componentId = "C+D"),
            WorkNode(idB, version = 1, linkedIds = Nil, componentId = "A+B"),
            WorkNode(idD, version = 1, linkedIds = Nil, componentId = "C+D")
          ))
        )
        .nodes shouldBe
        Set(
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idB,
            version = 2,
            linkedIds = List(idC),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = List(idD),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idD,
            version = 1,
            linkedIds = List(),
            componentId = ciHash(idA, idB, idC, idD))
        )
    }

    it("updating A->B with B->[C,D] gives A+B+C+D:(A->B, B->C&D, C, D") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idB, version = 2, referencedWorkIds = Set(idC, idD)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              version = 2,
              linkedIds = List(idB),
              componentId = "A+B"),
            WorkNode(
              idB,
              version = 1,
              linkedIds = Nil,
              componentId = ciHash(idA, idB)),
            WorkNode(
              idC,
              version = 1,
              linkedIds = Nil,
              componentId = ciHash(idC)),
            WorkNode(
              idD,
              version = 1,
              linkedIds = Nil,
              componentId = ciHash(idD))
          ))
        )
        .nodes shouldBe
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idB,
            version = 2,
            linkedIds = List(idC, idD),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = List(),
            componentId = ciHash(idA, idB, idC, idD)),
          WorkNode(
            idD,
            version = 1,
            linkedIds = List(),
            componentId = ciHash(idA, idB, idC, idD))
        )
    }

    it("updating A->B->C with A->C gives A+B+C:(A->B, B->C, C->A") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idC, version = 2, referencedWorkIds = Set(idA)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              version = 2,
              linkedIds = List(idB),
              componentId = "A+B+C"),
            WorkNode(
              idB,
              version = 2,
              linkedIds = List(idC),
              componentId = "A+B+C"),
            WorkNode(idC, version = 1, linkedIds = Nil, componentId = "A+B+C")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idB,
          version = 2,
          linkedIds = List(idC),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idC,
          version = 2,
          linkedIds = List(idA),
          componentId = ciHash(idA, idB, idC))
      )
    }
  }

  describe("Update version") {
    it("processes an update for a newer version") {
      val existingVersion = 1
      val updateVersion = 2
      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, updateVersion, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                existingVersion,
                linkedIds = Nil,
                componentId = ciHash(idA))))
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
            version = None,
            linkedIds = List(),
            componentId = ciHash(idA, idB)))
    }

    it("doesn't process an update for a lower version") {
      val existingVersion = 3
      val updateVersion = 1

      val thrown = intercept[VersionExpectedConflictException] {
        WorkGraphUpdater
          .update(
            links = WorkLinks(idA, updateVersion, referencedWorkIds = Set(idB)),
            existingGraph = WorkGraph(
              Set(
                WorkNode(
                  idA,
                  existingVersion,
                  linkedIds = Nil,
                  componentId = ciHash(idA))))
          )
      }
      thrown.message shouldBe s"update failed, work:$idA v1 is not newer than existing work v3"
    }

    it(
      "processes an update for the same version if it's the same as the one stored") {
      val existingVersion = 2
      val updateVersion = 2

      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, updateVersion, referencedWorkIds = Set(idB)),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                existingVersion,
                linkedIds = List(idB),
                componentId = ciHash(idA, idB)),
              WorkNode(
                idB,
                version = 0,
                linkedIds = List(),
                componentId = ciHash(idA, idB))))
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
            version = 0,
            linkedIds = List(),
            componentId = ciHash(idA, idB)))
    }

    it(
      "doesn't process an update for the same version if the work is different from the one stored") {
      val existingVersion = 2
      val updateVersion = 2

      val thrown = intercept[VersionUnexpectedConflictException] {
        WorkGraphUpdater
          .update(
            links = WorkLinks(idA, updateVersion, referencedWorkIds = Set(idA)),
            existingGraph = WorkGraph(
              Set(
                WorkNode(
                  idA,
                  existingVersion,
                  linkedIds = List(idB),
                  componentId = ciHash(idA, idB)),
                WorkNode(
                  idB,
                  version = 0,
                  linkedIds = List(),
                  componentId = ciHash(idA, idB))))
          )
      }
      thrown.getMessage shouldBe s"update failed, work:$idA v2 already exists with different content! update-ids:Set($idA) != existing-ids:Set($idB)"
    }
  }

  describe("Removing links") {
    it("updating  A->B with A gives A:A and B:B") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, version = 2, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = List(idB),
                componentId = "A+B"),
              WorkNode(
                idB,
                version = 1,
                linkedIds = List(),
                componentId = "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 2,
          linkedIds = List(),
          componentId = ciHash(idA)),
        WorkNode(
          idB,
          version = 1,
          linkedIds = List(),
          componentId = ciHash(idB))
      )
    }

    it("updating A->B with A but NO B (*should* not occur) gives A:A and B:B") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idA, version = 2, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = List(idB),
                componentId = "A+B")
            ))
        )
        .nodes shouldBe Set(
        WorkNode(idA, version = 2, linkedIds = Nil, componentId = ciHash(idA)),
        WorkNode(
          idB,
          version = None,
          linkedIds = Nil,
          componentId = ciHash(idB))
      )
    }

    it("updating A->B->C with B gives A+B:(A->B, B) and C:C") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idB, version = 3, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              version = 2,
              linkedIds = List(idB),
              componentId = "A+B+C"),
            WorkNode(
              idB,
              version = 2,
              linkedIds = List(idC),
              componentId = "A+B+C"),
            WorkNode(idC, version = 1, linkedIds = Nil, componentId = "A+B+C")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB)),
        WorkNode(
          idB,
          version = 3,
          linkedIds = Nil,
          componentId = ciHash(idA, idB)),
        WorkNode(idC, version = 1, linkedIds = Nil, componentId = ciHash(idC))
      )
    }

    it("updating A<->B->C with B->C gives A+B+C:(A->B, B->C, C)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks(idB, version = 3, referencedWorkIds = Set(idC)),
          existingGraph = WorkGraph(Set(
            WorkNode(
              idA,
              version = 2,
              linkedIds = List(idB),
              componentId = "A+B+C"),
            WorkNode(
              idB,
              version = 2,
              linkedIds = List(idA, idC),
              componentId = "A+B+C"),
            WorkNode(idC, version = 1, linkedIds = Nil, componentId = "A+B+C")
          ))
        )
        .nodes shouldBe Set(
        WorkNode(
          idA,
          version = 2,
          linkedIds = List(idB),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idB,
          version = 3,
          linkedIds = List(idC),
          componentId = ciHash(idA, idB, idC)),
        WorkNode(
          idC,
          version = 1,
          linkedIds = Nil,
          componentId = ciHash(idA, idB, idC))
      )
    }
  }
}
