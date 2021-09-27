package weco.pipeline.matcher.matcher

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.matcher.exceptions.MatcherException
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.MatcherResult
import weco.storage.locking.memory.{MemoryLockDao, MemoryLockingService}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkMatcherConcurrencyTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with WorkStubGenerators {

  it("processes one of two conflicting concurrent updates and locks the other") {
    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID]
    val lockingService =
      new MemoryLockingService[MatcherResult, Future]()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)
        val identifierA = createIdentifier(canonicalId = "AAAAAAAA")
        val identifierB = createIdentifier(canonicalId = "BBBBBBBB")

        val workA = createWorkStubWith(
          id = identifierA,
          referencedIds = Set(identifierB)
        )

        val workB = createWorkStubWith(
          id = identifierB
        )

        val eventualResultA = workMatcher.matchWork(workA)
        val eventualResultB = workMatcher.matchWork(workB)

        val eventualResults = for {
          resultA <- eventualResultA recoverWith {
            case e: MatcherException =>
              Future.successful(e)
          }
          resultB <- eventualResultB recoverWith {
            case e: MatcherException =>
              Future.successful(e)
          }
        } yield (resultA, resultB)

        whenReady(eventualResults) { results =>
          val resultsList = results.productIterator.toList
          val failure = resultsList.collect({
            case e: MatcherException => e
          })
          val result = resultsList.collect({
            case r: MatcherResult => r
          })

          failure.size shouldBe 1
          result.size shouldBe 1

          lockDao.locks shouldBe empty
        }
      }
    }
  }
}
