package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.CollectionPath
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.merger.fixtures.MergerFixtures


class WorkRouterSpec
  extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with MergerFixtures {

  it("uses pathConcatenatorSender for Merged works with a SierraSystemNumber") {
    val work_sierra = mergedWork(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.SierraSystemNumber,
        value = "sierra_id_1",
        ontologyType = "Work"
      ),
    ).collectionPath(CollectionPath("send/to/pathConcatenator"))

    workRouter(Left(work_sierra))
    getIncompletePathSent(workRouter.pathConcatenatorSender) shouldBe Seq("send/to/pathConcatenator")
  }

  it("uses pathSender for other Merged works") {
    val work_calm = mergedWork(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.CalmRecordIdentifier,
        value = "calm_id_1",
        ontologyType = "Work"
      )
    ).collectionPath(CollectionPath("send/to/pathSender"))

    workRouter(Left(work_calm))
    getPathsSent(workRouter.pathSender) shouldBe Seq("send/to/pathSender")
  }

  it("uses workSender for Denormalised works") {
    val work_no_collectionPath = denormalisedWork(canonicalId = CanonicalId("sierra_2"))

    workRouter(Right(work_no_collectionPath))
    getWorksSent(workRouter.workSender) shouldBe Seq("sierra_2")
  }
}

