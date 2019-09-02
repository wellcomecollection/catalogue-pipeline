package uk.ac.wellcome.platform.merger.fixtures

import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.bigmessaging.fixtures.VHSFixture
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.storage.store.HybridStoreEntry

trait LocalWorksVhs
    extends VHSFixture[TransformedBaseWork]
    with Eventually
    with ScalaFutures {

  def givenStoredInVhs(vhs: VHS, works: TransformedBaseWork*): Seq[Assertion] =
    works.map { work =>
      val entry = HybridStoreEntry(work, EmptyMetadata())
      vhs.upsert(work.sourceIdentifier.toString)(entry)(_ => entry)

      eventually {
        vhs.getLatest(work.sourceIdentifier.toString) match {
          case Left(error) => throw new Error(s"${error}")
          case Right(
              Identified(
                Version(_, version),
                HybridStoreEntry(storedWork, _))) =>
            storedWork shouldBe work
        }
      }
    }
}
