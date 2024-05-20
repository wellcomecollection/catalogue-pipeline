package weco.pipeline.matcher.workgraph

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.matcher.generators.WorkNodeGenerators
import weco.pipeline.matcher.models.{
  SourceWorkData,
  SubgraphId,
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkNode
}

class WorkGraphUpdaterTest
    extends AnyFunSpec
    with Matchers
    with WorkNodeGenerators {

  describe("Adding links without existing works") {
    it("updating nothing with A gives A:A") {
      val workA = createOneWork("A")

      val result = WorkGraphUpdater
        .update(
          work =
            createWorkWith(idA, version = 1, mergeCandidateIds = Set.empty),
          affectedNodes = Set()
        )

      result shouldBe Set(workA)
    }

    it("updating nothing with A->B gives A+B:A->B") {
      val workStubA =
        createWorkWith(idA, version = 1, mergeCandidateIds = Set(idB))

      val result = WorkGraphUpdater
        .update(
          work = workStubA,
          affectedNodes = Set()
        )

      result shouldBe Set(
        WorkNode(
          id = idA,
          subgraphId = SubgraphId(idA, idB),
          componentIds = List(idA, idB),
          sourceWork = SourceWorkData(
            id = workStubA.state.sourceIdentifier,
            version = 1,
            mergeCandidateIds = List(idB)
          )
        ),
        WorkNode(
          id = idB,
          subgraphId = SubgraphId(idA, idB),
          componentIds = List(idA, idB),
          sourceWork = None
        )
      )
    }
  }

  describe("Adding links to existing works") {
    it("updating A, B with A->B gives A+B:(A->B, B)") {
      val workA = createOneWork("A")
      val workB = createOneWork("B")

      val result = WorkGraphUpdater
        .update(
          work = createWorkWith(idA, version = 2, mergeCandidateIds = Set(idB)),
          affectedNodes = Set(workA, workB)
        )

      result shouldBe
        Set(
          workA
            .copy(
              subgraphId = SubgraphId(idA, idB),
              componentIds = List(idA, idB)
            )
            .updateSourceWork(version = 2, mergeCandidateIds = Set(idB)),
          workB.copy(
            subgraphId = SubgraphId(idA, idB),
            componentIds = List(idA, idB)
          )
        )
    }

    it("updating A->B with A->B gives A+B:(A->B, B)") {
      val (workA, workB) = createTwoWorks("A->B")

      val result = WorkGraphUpdater
        .update(
          work = createWorkWith(idA, version = 2, mergeCandidateIds = Set(idB)),
          affectedNodes = Set(workA, workB)
        )

      result shouldBe Set(
        workA.updateSourceWork(version = 2, mergeCandidateIds = Set(idB)),
        workB
      )
    }

    it("updating A->B, B, C with B->C gives A+B+C:(A->B, B->C, C)") {
      val (workA, workB) = createTwoWorks("A->B")
      val workC = createOneWork("C")

      val result = WorkGraphUpdater
        .update(
          work = createWorkWith(idB, version = 2, mergeCandidateIds = Set(idC)),
          affectedNodes = Set(workA, workB, workC)
        )

      result shouldBe Set(
        workA.copy(
          subgraphId = SubgraphId(idA, idB, idC),
          componentIds = List(idA, idB, idC)
        ),
        workB
          .copy(
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC)
          )
          .updateSourceWork(version = 2, mergeCandidateIds = Set(idC)),
        workC.copy(
          subgraphId = SubgraphId(idA, idB, idC),
          componentIds = List(idA, idB, idC)
        )
      )
    }

    it("updating A->B, C->D with B->C gives A+B+C+D:(A->B, B->C, C->D, D)") {
      val (workA, workB) = createTwoWorks("A->B")
      val (workC, workD) = createTwoWorks("C->D")

      val result = WorkGraphUpdater
        .update(
          work = createWorkWith(idB, version = 2, mergeCandidateIds = Set(idC)),
          affectedNodes = Set(workA, workB, workC, workD)
        )

      result shouldBe
        Set(
          workA.copy(
            subgraphId = SubgraphId(idA, idB, idC, idD),
            componentIds = List(idA, idB, idC, idD)
          ),
          workB
            .copy(
              subgraphId = SubgraphId(idA, idB, idC, idD),
              componentIds = List(idA, idB, idC, idD)
            )
            .updateSourceWork(version = 2, mergeCandidateIds = Set(idC)),
          workC.copy(
            subgraphId = SubgraphId(idA, idB, idC, idD),
            componentIds = List(idA, idB, idC, idD)
          ),
          workD.copy(
            subgraphId = SubgraphId(idA, idB, idC, idD),
            componentIds = List(idA, idB, idC, idD)
          )
        )
    }

    it("updating A->B with B->[C,D] gives A+B+C+D:(A->B, B->C&D, C, D") {
      val (workA, workB) = createTwoWorks("A->B")
      val workC = createOneWork("C")
      val workD = createOneWork("D")

      val result = WorkGraphUpdater
        .update(
          work =
            createWorkWith(idB, version = 2, mergeCandidateIds = Set(idC, idD)),
          affectedNodes = Set(workA, workB, workC, workD)
        )

      result shouldBe
        Set(
          workA.copy(
            subgraphId = SubgraphId(idA, idB, idC, idD),
            componentIds = List(idA, idB, idC, idD)
          ),
          workB
            .copy(
              subgraphId = SubgraphId(idA, idB, idC, idD),
              componentIds = List(idA, idB, idC, idD)
            )
            .updateSourceWork(version = 2, mergeCandidateIds = Set(idC, idD)),
          workC.copy(
            subgraphId = SubgraphId(idA, idB, idC, idD),
            componentIds = List(idA, idB, idC, idD)
          ),
          workD.copy(
            subgraphId = SubgraphId(idA, idB, idC, idD),
            componentIds = List(idA, idB, idC, idD)
          )
        )
    }

    it("updating A->B->C with C->A gives A+B+C:(A->B, B->C, C->A") {
      val (workA, workB, workC) = createThreeWorks("A->B->C")

      val result = WorkGraphUpdater
        .update(
          work = createWorkWith(idC, version = 2, mergeCandidateIds = Set(idA)),
          affectedNodes = Set(workA, workB, workC)
        )

      result shouldBe Set(
        workA,
        workB,
        workC.updateSourceWork(version = 2, mergeCandidateIds = Set(idA))
      )
    }
  }

  describe("Update version") {
    it("processes an update for a newer version") {
      val workA = createOneWork("A")

      val existingVersion = workA.sourceWork.get.version
      val updateVersion = existingVersion + 1

      val result = WorkGraphUpdater
        .update(
          work =
            createWorkWith(idA, updateVersion, mergeCandidateIds = Set(idB)),
          affectedNodes = Set(workA)
        )

      result shouldBe
        Set(
          workA
            .copy(
              subgraphId = SubgraphId(idA, idB),
              componentIds = List(idA, idB)
            )
            .updateSourceWork(
              version = updateVersion,
              mergeCandidateIds = Set(idB)
            ),
          WorkNode(
            idB,
            subgraphId = SubgraphId(idA, idB),
            componentIds = List(idA, idB)
          )
        )
    }

    it("doesn't process an update for a lower version") {
      val workA = createOneWork("A")

      val existingVersion = workA.sourceWork.get.version
      val updateVersion = existingVersion - 1

      val thrown = intercept[VersionExpectedConflictException] {
        WorkGraphUpdater
          .update(
            work =
              createWorkWith(idA, updateVersion, mergeCandidateIds = Set(idB)),
            affectedNodes = Set(workA)
          )
      }
      thrown.message shouldBe s"update failed, work:$idA v$updateVersion is not newer than existing work v$existingVersion"
    }

    it(
      "processes an update for the same version if it's the same as the one stored"
    ) {
      val (workA, workB) = createTwoWorks("A->B")

      val existingVersion = workA.sourceWork.get.version

      val result = WorkGraphUpdater
        .update(
          work =
            createWorkWith(idA, existingVersion, mergeCandidateIds = Set(idB)),
          affectedNodes = Set(workA, workB)
        )

      result shouldBe Set(workA, workB)
    }

    it(
      "doesn't process an update for the same version if the work is different from the one stored"
    ) {
      val (workA, workB) = createTwoWorks("A->B")

      val existingVersion = workA.sourceWork.get.version

      val thrown = intercept[VersionUnexpectedConflictException] {
        WorkGraphUpdater
          .update(
            work = createWorkWith(
              idA,
              version = existingVersion,
              mergeCandidateIds = Set(idC)
            ),
            affectedNodes = Set(workA, workB)
          )
      }
      thrown.getMessage shouldBe s"update failed, work:$idA v$existingVersion already exists with different content! update-ids:Set($idC) != existing-ids:Set($idB)"
    }
  }

  describe("Removing links") {
    it("updating A->B with A gives A:A and B:B") {
      val (workA, workB) = createTwoWorks("A->B")

      val result = WorkGraphUpdater
        .update(
          work =
            createWorkWith(idA, version = 2, mergeCandidateIds = Set.empty),
          affectedNodes = Set(workA, workB)
        )

      result shouldBe Set(
        workA
          .copy(componentIds = List(idA))
          .updateSourceWork(version = 2),
        workB
          .copy(componentIds = List(idB))
      )
    }

    it("updating A->B->C with B gives A+B:(A->B, B) and C:C") {
      val (workA, workB, workC) = createThreeWorks("A->B->C")

      val result = WorkGraphUpdater
        .update(
          work =
            createWorkWith(idB, version = 2, mergeCandidateIds = Set.empty),
          affectedNodes = Set(workA, workB, workC)
        )

      result shouldBe Set(
        workA
          .copy(componentIds = List(idA, idB)),
        workB
          .copy(componentIds = List(idA, idB))
          .updateSourceWork(version = 2),
        workC
          .copy(componentIds = List(idC))
      )
    }

    it("updating A<->B->C with B->C gives A+B+C:(A->B, B->C, C)") {
      val (workA, workB, workC) = createThreeWorks("A<->B->C")

      val result = WorkGraphUpdater
        .update(
          work = createWorkWith(idB, version = 2, mergeCandidateIds = Set(idC)),
          affectedNodes = Set(workA, workB, workC)
        )

      result shouldBe Set(
        workA,
        workB.updateSourceWork(version = 2, mergeCandidateIds = Set(idC)),
        workC
      )
    }
  }

  describe("handling suppressed works") {
    it("A->B, but B is suppressed (updating A)") {
      val workA = createWorkWith(idA, version = 1, mergeCandidateIds = Set(idB))
      val workB = createOneWork("B[suppressed]")

      val result =
        WorkGraphUpdater.update(
          work = workA,
          affectedNodes = Set(workB)
        )

      result shouldBe Set(
        WorkNode(
          id = idA,
          subgraphId = SubgraphId(idA, idB),
          componentIds = List(idA),
          sourceWork = SourceWorkData(
            id = workA.state.sourceIdentifier,
            version = 1,
            mergeCandidateIds = List(idB)
          )
        ),
        workB.copy(
          subgraphId = SubgraphId(idA, idB),
          componentIds = List(idB)
        )
      )
    }

    it("A->B, but B is suppressed (updating B)") {
      val (workA, workB) = createTwoWorks("A->B")

      val result =
        WorkGraphUpdater.update(
          work = createWorkWith(
            idB,
            version = 2,
            mergeCandidateIds = Set(idB),
            workType = "Deleted"
          ),
          affectedNodes = Set(workA, workB)
        )

      result shouldBe Set(
        workA
          .copy(componentIds = List(idA)),
        workB
          .copy(componentIds = List(idB))
          .updateSourceWork(version = 2, suppressed = true)
      )
    }

    it("A->B->C->D->E, but C is suppressed (updating A)") {
      val (workA, workB, workC, workD, workE) = createFiveWorks("A->B->C->D->E")
      val suppressedWorkC = workC.copy(
        sourceWork = Some(workC.sourceWork.get.copy(suppressed = true))
      )

      val result =
        WorkGraphUpdater.update(
          work = createWorkWith(idA, version = 2, mergeCandidateIds = Set(idB)),
          affectedNodes = Set(workA, workB, suppressedWorkC, workD, workE)
        )

      result shouldBe Set(
        workA
          .copy(componentIds = List(idA, idB))
          .updateSourceWork(version = 2, mergeCandidateIds = Set(idB)),
        workB
          .copy(componentIds = List(idA, idB)),
        suppressedWorkC
          .copy(componentIds = List(idC)),
        workD
          .copy(componentIds = List(idD, idE)),
        workE
          .copy(componentIds = List(idD, idE))
      )
    }

    it("A->B->C, B is suppressed, then B is updated as unsuppressed") {
      val graph1 =
        WorkGraphUpdater.update(
          work = createWorkWith(idA, version = 1, mergeCandidateIds = Set(idB)),
          affectedNodes = Set()
        )

      val graph2 =
        WorkGraphUpdater.update(
          work = createWorkWith(
            idB,
            version = 1,
            mergeCandidateIds = Set(idC),
            workType = "Deleted"
          ),
          affectedNodes = graph1
        )

      val graph3 =
        WorkGraphUpdater.update(
          work = createWorkWith(idC, version = 1, mergeCandidateIds = Set()),
          affectedNodes = graph2
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
            mergeCandidateIds = Set(idC),
            workType = "Undeleted"
          ),
          affectedNodes = graph3
        )

      result.map(_.subgraphId) shouldBe Set(SubgraphId(idA, idB, idC))
    }
  }
}
