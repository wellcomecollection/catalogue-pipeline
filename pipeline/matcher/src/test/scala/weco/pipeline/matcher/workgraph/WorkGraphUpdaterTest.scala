package weco.pipeline.matcher.workgraph

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{
  ComponentId,
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkNode
}

class WorkGraphUpdaterTest
    extends AnyFunSpec
    with Matchers
    with WorkStubGenerators {

  describe("Adding links without existing works") {
    it("updating nothing with A gives A:A") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, version = 1, referencedWorkIds = Set.empty),
            affectedNodes = Set()
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA)))
      )
    }

    it("updating nothing with A->B gives A+B:A->B") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, version = 1, referencedWorkIds = Set(idB)),
            affectedNodes = Set()
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idB,
            version = None,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it("updating nothing with B->A gives A+B:B->A") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idB, version = 1, referencedWorkIds = Set(idA)),
            affectedNodes = Set()
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(idA),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idA,
            version = None,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }
  }

  describe("Adding links to existing works") {
    it("updating A, B with A->B gives A+B:(A->B, B)") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, version = 2, referencedWorkIds = Set(idB)),
            affectedNodes = Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idA)),
              WorkNode(
                idB,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idB))
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it("updating A->B with A->B gives A+B:(A->B, B)") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, version = 2, referencedWorkIds = Set(idB)),
            affectedNodes = Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = List(idB),
                componentId = ComponentId(idA, idB)),
              WorkNode(
                idB,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idA, idB)))
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it("updating A->B, B, C with B->C gives A+B+C:(A->B, B->C, C)") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idB, version = 2, referencedWorkIds = Set(idC)),
            affectedNodes = Set(
              WorkNode(
                idA,
                version = 2,
                linkedIds = List(idB),
                componentId = "A+B"),
              WorkNode(
                idB,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idA, idB)),
              WorkNode(
                idC,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idC))
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idB,
            version = 2,
            linkedIds = List(idC),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB, idC))
        )
      )
    }

    it("updating A->B, C->D with B->C gives A+B+C+D:(A->B, B->C, C->D, D)") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idB, version = 2, referencedWorkIds = Set(idC)),
            affectedNodes = Set(
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
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC, idD)),
          WorkNode(
            idB,
            version = 2,
            linkedIds = List(idC),
            componentId = ComponentId(idA, idB, idC, idD)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = List(idD),
            componentId = ComponentId(idA, idB, idC, idD)),
          WorkNode(
            idD,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB, idC, idD))
        )
      )
    }

    it("updating A->B with B->[C,D] gives A+B+C+D:(A->B, B->C&D, C, D") {
      val result =
        WorkGraphUpdater
          .update(
            work = createWorkWith(
              idB,
              version = 2,
              referencedWorkIds = Set(idC, idD)),
            affectedNodes = Set(
              WorkNode(
                idA,
                version = 2,
                linkedIds = List(idB),
                componentId = "A+B"),
              WorkNode(
                idB,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idA, idB)),
              WorkNode(
                idC,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idC)),
              WorkNode(
                idD,
                version = 1,
                linkedIds = Nil,
                componentId = ComponentId(idD))
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC, idD)),
          WorkNode(
            idB,
            version = 2,
            linkedIds = List(idC, idD),
            componentId = ComponentId(idA, idB, idC, idD)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB, idC, idD)),
          WorkNode(
            idD,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB, idC, idD))
        )
      )
    }

    it("updating A->B->C with A->C gives A+B+C:(A->B, B->C, C->A") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idC, version = 2, referencedWorkIds = Set(idA)),
            affectedNodes = Set(
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
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idB,
            version = 2,
            linkedIds = List(idC),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idC,
            version = 2,
            linkedIds = List(idA),
            componentId = ComponentId(idA, idB, idC))
        )
      )
    }
  }

  describe("Update version") {
    it("processes an update for a newer version") {
      val existingVersion = 1
      val updateVersion = 2

      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, updateVersion, referencedWorkIds = Set(idB)),
            affectedNodes = Set(
              WorkNode(
                idA,
                existingVersion,
                linkedIds = Nil,
                componentId = ComponentId(idA)))
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            updateVersion,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idB,
            version = None,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it("doesn't process an update for a lower version") {
      val existingVersion = 3
      val updateVersion = 1

      val thrown = intercept[VersionExpectedConflictException] {
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, updateVersion, referencedWorkIds = Set(idB)),
            affectedNodes = Set(
              WorkNode(
                idA,
                existingVersion,
                linkedIds = Nil,
                componentId = ComponentId(idA)))
          )
      }
      thrown.message shouldBe s"update failed, work:$idA v1 is not newer than existing work v3"
    }

    it(
      "processes an update for the same version if it's the same as the one stored") {
      val existingVersion = 2
      val updateVersion = 2

      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, updateVersion, referencedWorkIds = Set(idB)),
            affectedNodes = Set(
              WorkNode(
                idA,
                existingVersion,
                linkedIds = List(idB),
                componentId = ComponentId(idA, idB)),
              WorkNode(
                idB,
                version = 0,
                linkedIds = List(),
                componentId = ComponentId(idA, idB))
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            updateVersion,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idB,
            version = 0,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it(
      "doesn't process an update for the same version if the work is different from the one stored") {
      val existingVersion = 2
      val updateVersion = 2

      val thrown = intercept[VersionUnexpectedConflictException] {
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, updateVersion, referencedWorkIds = Set(idC)),
            affectedNodes = Set(
              WorkNode(
                idA,
                existingVersion,
                linkedIds = List(idB),
                componentId = ComponentId(idA, idB)),
              WorkNode(
                idB,
                version = 0,
                linkedIds = List(),
                componentId = ComponentId(idA, idB))
            )
          )
      }
      thrown.getMessage shouldBe s"update failed, work:$idA v2 already exists with different content! update-ids:Set($idC) != existing-ids:Set($idB)"
    }
  }

  describe("Removing links") {
    it("updating A->B with A gives A:A and B:B") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, version = 2, referencedWorkIds = Set.empty),
            affectedNodes = Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = List(idB),
                componentId = "A+B"),
              WorkNode(
                idB,
                version = 1,
                linkedIds = List(),
                componentId = "A+B"))
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        ),
        Set(
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it("updating A->B with A but NO B (*should* not occur) gives A:A and B:B") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, version = 2, referencedWorkIds = Set.empty),
            affectedNodes = Set(
              WorkNode(
                idA,
                version = 1,
                linkedIds = List(idB),
                componentId = "A+B")
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB))
        ),
        Set(
          WorkNode(
            idB,
            version = None,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB))
        )
      )
    }

    it("updating A->B->C with B gives A+B:(A->B, B) and C:C") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idB, version = 3, referencedWorkIds = Set.empty),
            affectedNodes = (
              Set(
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
                WorkNode(
                  idC,
                  version = 1,
                  linkedIds = Nil,
                  componentId = "A+B+C")
              )
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idB,
            version = 3,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB, idC)),
        ),
        Set(
          WorkNode(
            idC,
            version = 1,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB, idC))
        )
      )
    }

    it("updating A<->B->C with B->C gives A+B+C:(A->B, B->C, C)") {
      val result =
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idB, version = 3, referencedWorkIds = Set(idC)),
            affectedNodes = Set(
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
            )
          )

      result shouldBe Set(
        Set(
          WorkNode(
            idA,
            version = 2,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idB,
            version = 3,
            linkedIds = List(idC),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB, idC))
        )
      )
    }
  }

  describe("handling suppressed works") {
    it("A → B, but B is suppressed (updating A)") {
      val result =
        WorkGraphUpdater.update(
          work = createWorkWith(idA, version = 1, referencedWorkIds = Set(idB)),
          affectedNodes = Set(
            WorkNode(
              id = idB,
              version = 1,
              linkedIds = List(),
              componentId = ComponentId(idB),
              suppressed = true)
          )
        )

      result shouldBe Set(
        Set(
          WorkNode(
            id = idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB))
        ),
        Set(
          WorkNode(
            id = idB,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB),
            suppressed = true)
        )
      )
    }

    it("A → B, but B is suppressed (updating B)") {
      val result =
        WorkGraphUpdater.update(
          work = createWorkWith(
            idB,
            version = 1,
            referencedWorkIds = Set(idB),
            workType = "Deleted"),
          affectedNodes = Set(
            WorkNode(
              id = idA,
              version = 1,
              linkedIds = List(idB),
              componentId = ComponentId(idA, idB)),
            WorkNode(
              id = idB,
              version = None,
              linkedIds = List(),
              componentId = ComponentId(idA, idB))
          )
        )

      result shouldBe Set(
        Set(
          WorkNode(
            id = idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB))
        ),
        Set(
          WorkNode(
            id = idB,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB),
            suppressed = true)
        )
      )
    }

    it("A → B → C → D → E, but C is suppressed (updating A)") {
      val result =
        WorkGraphUpdater.update(
          work = createWorkWith(idA, version = 1, referencedWorkIds = Set(idB)),
          affectedNodes = Set(
            WorkNode(
              id = idB,
              version = 1,
              linkedIds = List(idC),
              componentId = ComponentId(idB)),
            WorkNode(
              id = idC,
              version = 1,
              linkedIds = List(idD),
              componentId = ComponentId(idC),
              suppressed = true),
            WorkNode(
              id = idD,
              version = 1,
              linkedIds = List(idE),
              componentId = ComponentId(idD, idE)),
            WorkNode(
              id = idE,
              version = 1,
              linkedIds = List(),
              componentId = ComponentId(idD, idE))
          )
        )

      result shouldBe Set(
        Set(
          WorkNode(
            id = idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC, idD, idE)),
          WorkNode(
            id = idB,
            version = 1,
            linkedIds = List(idC),
            componentId = ComponentId(idA, idB, idC, idD, idE)),
        ),
        Set(
          WorkNode(
            id = idC,
            version = 1,
            linkedIds = List(idD),
            componentId = ComponentId(idA, idB, idC, idD, idE),
            suppressed = true),
        ),
        Set(
          WorkNode(
            id = idD,
            version = 1,
            linkedIds = List(idE),
            componentId = ComponentId(idA, idB, idC, idD, idE)),
          WorkNode(
            id = idE,
            version = 1,
            linkedIds = List(),
            componentId = ComponentId(idA, idB, idC, idD, idE))
        )
      )
    }

    it("A → B → C, B is suppressed, then B is updated as unsuppressed") {
      val graph1 =
        WorkGraphUpdater.update(
          work = createWorkWith(idA, version = 1, referencedWorkIds = Set(idB)),
          affectedNodes = Set()
        )

      val graph2 =
        WorkGraphUpdater.update(
          work = createWorkWith(
            idB,
            version = 1,
            referencedWorkIds = Set(idC),
            workType = "Deleted"),
          affectedNodes = graph1.flatten
        )

      val graph3 =
        WorkGraphUpdater.update(
          work = createWorkWith(idC, version = 1, referencedWorkIds = Set()),
          affectedNodes = graph2.flatten
        )

      // At this point the graph database knows about all three of A/B/C, but it should
      // be storing them as separate components.
      //
      // Now if we update B and B only, we should see the graphs be merged into a single component --
      // that is, the graph remembers that A → B, even though it wasn't actively using that
      // information for the matcher result.
      val result =
        WorkGraphUpdater.update(
          work = createWorkWith(
            idB,
            version = 2,
            referencedWorkIds = Set(idC),
            workType = "Undeleted"),
          affectedNodes = graph3.flatten
        )

      result.flatten.map(_.componentId) shouldBe Set(ComponentId(idA, idB, idC))
    }
  }
}
