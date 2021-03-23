package uk.ac.wellcome.platform.matcher.workgraph

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models._

class WorkGraphUpdaterTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures {

  // An existing graph of works is updated by changing the links of a single work.
  // The change may result in new compound works which are identified by the LinkedWorkGraphUpdater
  // works are shown by id with directed links, A->B means work with id "A" is linked to work with id "B"
  // works comprised of linked works are identified by compound id together with their linked works.
  // A+B:(A->B, B) means compound work with id "A+B" is made of work A linked to B and work B.

  private val hashed_A =
    "559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd" // ciHash("A")
  private val hashed_B =
    "df7e70e5021544f4834bbee64a9e3789febc4be81470df629cad6ddb03320a5c" // ciHash("B")
  private val hashed_C =
    "6b23c0d5f35d1b11f9b683f0b0a617355deb11277d91ae091d399c655b87940d" // ciHash("C")
  private val hashed_AB =
    "ca5b7e1c5b0ddba53ac5a73e3c49e9bb896a6f488ce4d605d24a6debedcc901d" // ciHash("A+B")
  private val hashed_ABC =
    "fe153f639ec1439fad84263be471b79485d2356a52871798a68d221596f06cef" // ciHash("A+B+C")
  private val hashed_ABCD =
    "317c89a844216f56119b9ffa835390fe92e75a2c13f7d29026327bf3318560bd" // ciHash("A+B+C+D")

  describe("Adding links without existing works") {
    it("updating nothing with A gives A:A") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", 1, Set.empty),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(WorkNode("A", version = 1, linkedIds = List(), componentId = hashed_A))
    }

    it("updating nothing with A->B gives A+B:A->B") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", 1, Set("B")),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 1, linkedIds = List("B"), componentId = hashed_AB),
        WorkNode("B", version = None, linkedIds = List(), componentId = hashed_AB))
    }

    it("updating nothing with B->A gives A+B:B->A") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("B", version = 1, referencedWorkIds = Set("A")),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode("B", version = 1, linkedIds = List("A"), componentId = hashed_AB),
        WorkNode("A", version = None, linkedIds = List(), componentId = hashed_AB))
    }
  }

  describe("Adding links to existing works") {
    it("updating A, B with A->B gives A+B:(A->B, B)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", version = 2, referencedWorkIds = Set("B")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 1, linkedIds = Nil, componentId = "A"),
              WorkNode("B", version = 1, linkedIds = Nil, componentId = "B")
            )
          )
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_AB),
          WorkNode("B", version = 1, linkedIds = List(), componentId = hashed_AB))
    }

    it("updating A->B with A->B gives A+B:(A->B, B)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", version = 2, referencedWorkIds = Set("B")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 1, linkedIds = List("B"), componentId = "A+B"),
              WorkNode("B", version = 1, linkedIds = Nil, componentId = "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_AB),
        WorkNode("B", version = 1, linkedIds = List(), componentId = hashed_AB)
      )
    }

    it("updating A->B, B, C with B->C gives A+B+C:(A->B, B->C, C)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("B", 2, Set("C")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 2, linkedIds = List("B"), componentId = "A+B"),
              WorkNode("B", version = 1, linkedIds = Nil, componentId = "A+B"),
              WorkNode("C", version = 1, linkedIds = Nil, componentId = "C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_ABC),
        WorkNode("B", version = 2, linkedIds = List("C"), componentId = hashed_ABC),
        WorkNode("C", version = 1, linkedIds = List(), componentId = hashed_ABC)
      )
    }

    it("updating A->B, C->D with B->C gives A+B+C+D:(A->B, B->C, C->D, D)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("B", version = 2, referencedWorkIds = Set("C")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 1, linkedIds = List("B"), componentId = "A+B"),
              WorkNode("C", version = 1, linkedIds = List("D"), componentId = "C+D"),
              WorkNode("B", version = 1, linkedIds = Nil, componentId = "A+B"),
              WorkNode("D", version = 1, linkedIds = Nil, componentId = "C+D")
            ))
        )
        .nodes shouldBe
        Set(
          WorkNode("A", version = 1, linkedIds = List("B"), componentId = hashed_ABCD),
          WorkNode("B", version = 2, linkedIds = List("C"), componentId = hashed_ABCD),
          WorkNode("C", version = 1, linkedIds = List("D"), componentId = hashed_ABCD),
          WorkNode("D", version = 1, linkedIds = List(), componentId = hashed_ABCD))
    }

    it("updating A->B with B->[C,D] gives A+B+C+D:(A->B, B->C&D, C, D") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("B", version = 2, referencedWorkIds = Set("C", "D")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 2, linkedIds = List("B"), componentId = "A+B"),
              WorkNode("B", version = 1, linkedIds = Nil, componentId = "A+B"),
              WorkNode("C", version = 1, linkedIds = Nil, componentId = "C"),
              WorkNode("D", version = 1, linkedIds = Nil, componentId = "D")
            ))
        )
        .nodes shouldBe
        Set(
          WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_ABCD),
          WorkNode("B", version = 2, linkedIds = List("C", "D"), componentId = hashed_ABCD),
          WorkNode("C", version = 1, linkedIds = List(), componentId = hashed_ABCD),
          WorkNode("D", version = 1, linkedIds = List(), componentId = hashed_ABCD)
        )
    }

    it("updating A->B->C with A->C gives A+B+C:(A->B, B->C, C->A") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("C", version = 2, referencedWorkIds = Set("A")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 2, linkedIds = List("B"), componentId = "A+B+C"),
              WorkNode("B", version = 2, linkedIds = List("C"), componentId = "A+B+C"),
              WorkNode("C", version = 1, linkedIds = Nil, componentId = "A+B+C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_ABC),
        WorkNode("B", version = 2, linkedIds = List("C"), componentId = hashed_ABC),
        WorkNode("C", version = 2, linkedIds = List("A"), componentId = hashed_ABC)
      )
    }
  }

  describe("Update version") {
    it("processes an update for a newer version") {
      val existingVersion = 1
      val updateVersion = 2
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", updateVersion, referencedWorkIds = Set("B")),
          existingGraph =
            WorkGraph(Set(WorkNode("A", existingVersion, linkedIds = Nil, componentId = "A")))
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode("A", updateVersion, linkedIds = List("B"), componentId = hashed_AB),
          WorkNode("B", version = None, linkedIds = List(), componentId = hashed_AB))
    }

    it("doesn't process an update for a lower version") {
      val existingVersion = 3
      val updateVersion = 1

      val thrown = intercept[VersionExpectedConflictException] {
        WorkGraphUpdater
          .update(
            links = WorkLinks("A", updateVersion, referencedWorkIds = Set("B")),
            existingGraph =
              WorkGraph(Set(WorkNode("A", existingVersion, linkedIds = Nil, componentId = "A")))
          )
      }
      thrown.message shouldBe "update failed, work:A v1 is not newer than existing work v3"
    }

    it(
      "processes an update for the same version if it's the same as the one stored") {
      val existingVersion = 2
      val updateVersion = 2

      WorkGraphUpdater
        .update(
          links = WorkLinks("A", updateVersion, referencedWorkIds = Set("B")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", existingVersion, linkedIds = List("B"), componentId = hashed_AB),
              WorkNode("B", version = 0, linkedIds = List(), componentId = hashed_AB)))
        )
        .nodes should contain theSameElementsAs
        List(
          WorkNode("A", updateVersion, linkedIds = List("B"), componentId = hashed_AB),
          WorkNode("B", version = 0, linkedIds = List(), componentId = hashed_AB))
    }

    it(
      "doesn't process an update for the same version if the work is different from the one stored") {
      val existingVersion = 2
      val updateVersion = 2

      val thrown = intercept[VersionUnexpectedConflictException] {
        WorkGraphUpdater
          .update(
            links = WorkLinks("A", updateVersion, referencedWorkIds = Set("A")),
            existingGraph = WorkGraph(
              Set(
                WorkNode("A", existingVersion, linkedIds = List("B"), componentId = hashed_AB),
                WorkNode("B", version = 0, linkedIds = List(), componentId = hashed_AB)))
          )
      }
      thrown.getMessage shouldBe "update failed, work:A v2 already exists with different content! update-ids:Set(A) != existing-ids:Set(B)"
    }
  }

  describe("Removing links") {
    it("updating  A->B with A gives A:A and B:B") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", version = 2, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 1, linkedIds = List("B"), componentId = "A+B"),
              WorkNode("B", version = 1, linkedIds = List(), componentId = "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = List(), componentId = hashed_A),
        WorkNode("B", version = 1, linkedIds = List(), componentId = hashed_B)
      )
    }

    it("updating A->B with A but NO B (*should* not occur) gives A:A and B:B") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("A", version = 2, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 1, linkedIds = List("B"), componentId = "A+B")
            ))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = Nil, componentId = hashed_A),
        WorkNode("B", version = None, linkedIds = Nil, componentId = hashed_B)
      )
    }

    it("updating A->B->C with B gives A+B:(A->B, B) and C:C") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("B", version = 3, referencedWorkIds = Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 2, linkedIds = List("B"), componentId = "A+B+C"),
              WorkNode("B", version = 2, linkedIds = List("C"), componentId = "A+B+C"),
              WorkNode("C", version = 1, linkedIds = Nil, componentId = "A+B+C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_AB),
        WorkNode("B", version = 3, linkedIds = Nil, componentId = hashed_AB),
        WorkNode("C", version = 1, linkedIds = Nil, componentId = hashed_C)
      )
    }

    it("updating A<->B->C with B->C gives A+B+C:(A->B, B->C, C)") {
      WorkGraphUpdater
        .update(
          links = WorkLinks("B", version = 3, referencedWorkIds = Set("C")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", version = 2, linkedIds = List("B"), componentId = "A+B+C"),
              WorkNode("B", version = 2, linkedIds = List("A", "C"), componentId = "A+B+C"),
              WorkNode("C", version = 1, linkedIds = Nil, componentId = "A+B+C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", version = 2, linkedIds = List("B"), componentId = hashed_ABC),
        WorkNode("B", version = 3, linkedIds = List("C"), componentId = hashed_ABC),
        WorkNode("C", version = 1, linkedIds = Nil, componentId = hashed_ABC)
      )
    }
  }
}
