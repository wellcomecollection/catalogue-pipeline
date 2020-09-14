package uk.ac.wellcome.platform.merger.fixtures

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.bigmessaging.fixtures.VHSFixture
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.storage.Identified
import WorkState.Unidentified

trait LocalWorksVhs extends VHSFixture[Work[Unidentified]] with Matchers {
  def givenStoredInVhs(vhs: VHS, works: Work[Unidentified]*): Seq[Assertion] =
    works.map { work =>
      vhs.init(work.sourceIdentifier.toString)(work)

      vhs.getLatest(work.sourceIdentifier.toString) match {
        case Left(error) => throw new Error(s"$error")
        case Right(Identified(_, storedWork)) =>
          storedWork shouldBe work
      }
    }
}
