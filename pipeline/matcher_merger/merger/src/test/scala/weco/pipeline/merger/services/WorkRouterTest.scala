package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.merger.fixtures.MergerFixtures


class WorkRouterSpec
  extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with MergerFixtures {
  it("uses pathConcatenatorSender for Merged works with a SierraSystemNumber") {
    val work_sierra = mergedWork(canonicalId = CanonicalId("sierra_1"))

    workRouter(Left(work_sierra))
    getIncompletePathSent(workRouter.pathConcatenatorSender) shouldBe Seq(work_sierra.data.collectionPath.map(_.path).toString)
  }

  it("uses pathSender for other Merged works") {
    val work_calm = mergedWork(canonicalId = CanonicalId("calm_123"))

    workRouter(Left(work_calm))
    getPathsSent(workRouter.pathSender) shouldBe Seq(work_calm.data.collectionPath.map(_.path).toString)
  }

  it("uses workSender for Denormalised works") {
    val work_no_collectionPath = denormalisedWork(canonicalId = CanonicalId("sierra_2"))

    workRouter(Right(work_no_collectionPath))
    getWorksSent(workRouter.workSender) shouldBe Seq("sierra_2")
  }
}

