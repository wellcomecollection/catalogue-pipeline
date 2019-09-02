package uk.ac.wellcome.platform.merger.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.merger.fixtures.LocalWorksVhs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RecorderPlaybackServiceTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with LocalWorksVhs
    with WorksGenerators {

  it("fetches a single Work") {
    val work = createUnidentifiedWork

    withVHS { vhs =>
      givenStoredInVhs(vhs, work)

      whenReady(fetchAllWorks(vhs = vhs, work)) { result =>
        result shouldBe Seq(Some(work))
      }
    }
  }

  it("throws an error if asked to fetch a missing entry") {
    val work = createUnidentifiedWork

    withVHS { vhs =>
      whenReady(fetchAllWorks(vhs = vhs, work).failed) { result =>
        result shouldBe a[NoSuchElementException]
        result.getMessage shouldBe s"Work ${work.sourceIdentifier} is not in VHS!"
      }
    }
  }

  it("returns None if asked to fetch a Work with version 0") {
    val work = createUnidentifiedWorkWith(version = 0)

    withVHS { vhs =>
      whenReady(fetchAllWorks(vhs = vhs, work)) { result =>
        result shouldBe Seq(None)
      }
    }
  }

  it("returns None if the version in VHS has a higher version") {
    val work = createUnidentifiedWorkWith(version = 2)

    val workToStore = createUnidentifiedWorkWith(
      sourceIdentifier = work.sourceIdentifier,
      version = work.version + 1
    )

    withVHS { vhs =>
      givenStoredInVhs(vhs, workToStore)

      whenReady(fetchAllWorks(vhs = vhs, work)) { result =>
        result shouldBe Seq(None)
      }
    }
  }

  it("gets a mixture of works as appropriate") {
    val unchangedWorks = (1 to 3).map { _ =>
      createUnidentifiedWork
    }
    val outdatedWorks = (4 to 5).map { _ =>
      createUnidentifiedWork
    }
    val updatedWorks = outdatedWorks.map { work =>
      work.copy(version = work.version + 1)
    }
    val zeroWorks = (6 to 7).map { _ =>
      createUnidentifiedWorkWith(version = 0)
    }

    val lookupWorks = (unchangedWorks ++ outdatedWorks ++ zeroWorks).toList
    val storedWorks = (unchangedWorks ++ updatedWorks ++ zeroWorks).toList

    withVHS { vhs =>
      givenStoredInVhs(vhs, storedWorks: _*)

      whenReady(fetchAllWorks(vhs = vhs, lookupWorks: _*)) { result =>
        result shouldBe (unchangedWorks.map { Some(_) } ++ (4 to 7).map { _ =>
          None
        })
      }
    }
  }

  private def fetchAllWorks(
    vhs: VHS,
    works: TransformedBaseWork*): Future[Seq[Option[TransformedBaseWork]]] = {
    val service = new RecorderPlaybackService(vhs)

    val workIdentifiers = works
      .map { w =>
        WorkIdentifier(w)
      }

    service.fetchAllWorks(workIdentifiers)
  }
}
