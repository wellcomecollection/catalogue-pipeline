package uk.ac.wellcome.platform.merger.fixtures

import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.bigmessaging.fixtures.VHSFixture
import uk.ac.wellcome.storage.Identified

trait LocalWorksVhs
    extends VHSFixture[TransformedBaseWork]
    with Eventually
    with ScalaFutures {

  def givenStoredInVhs(vhs: VHS, works: TransformedBaseWork*): Seq[Assertion] =
    works.map { work =>
      vhs.init(work.sourceIdentifier.toString)(work)

      vhs.getLatest(work.sourceIdentifier.toString) match {
        case Left(error) => throw new Error(s"${error}")
        case Right(Identified(_, storedWork)) =>
          storedWork shouldBe work
      }
    }
}
