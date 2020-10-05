package uk.ac.wellcome.platform.matcher.storage

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.fixtures.VHSFixture
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.models.VersionExpectedConflictException
import uk.ac.wellcome.storage.Version
import WorkState.Source

class WorkStoreTest
    extends AnyFunSpec
    with Matchers
    with VHSFixture[Work[Source]]
    with WorkGenerators
    with Inside {
  it("gets a work from vhs") {
    withVHS { vhs: VHS =>
      val workStore = new WorkStore(vhs)
      val expectedWork = sourceWork()
      val actualWork = for {
        key <- vhs.put(Version("b12345678", 1))(expectedWork)
        work <- workStore.getWork(key.id)
      } yield work
      actualWork shouldBe Right(expectedWork)
    }
  }

  it("return a Left if it can't find the work in vhs") {
    withVHS { vhs: VHS =>
      val workStore = new WorkStore(vhs)
      val actualWork = for {
        work <- workStore.getWork(Version("b12345678", 1))
      } yield work
      actualWork shouldBe a[Left[_, _]]
    }
  }

  it(
    "returns a VersionExpectedConflictException if the work exists in VHS with a higher version") {
    withVHS { vhs: VHS =>
      val workStore = new WorkStore(vhs)
      val expectedWork = sourceWork()
      val actualWork = for {
        _ <- vhs.put(Version("b12345678", 2))(expectedWork)
        work <- workStore.getWork(Version("b12345678", 1))
      } yield work
      inside(actualWork) {
        case Left(MatcherException(e)) =>
          e shouldBe a[VersionExpectedConflictException]
      }
    }

  }

  it("returns a left if the work is in VHS with a lower version") {
    withVHS { vhs: VHS =>
      val workStore = new WorkStore(vhs)
      val expectedWork = sourceWork()
      val actualWork = for {
        _ <- vhs.put(Version("b12345678", 1))(expectedWork)
        work <- workStore.getWork(Version("b12345678", 2))
      } yield work
      actualWork shouldBe a[Left[_, _]]
    }
  }

}
