package weco.pipeline.matcher.matcher

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
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

  it(
    "processes one of two conflicting concurrent updates and locks the other"
  ) {
    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID]
    val lockingService =
      new MemoryLockingService[MatcherResult, Future]()

    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            val workMatcher = new WorkMatcher(workGraphStore, lockingService)

            val workA = createWorkWith(
              id = idA,
              mergeCandidateIds = Set(idB)
            )

            val workB = createWorkWith(id = idB)

            val eventualResultA = workMatcher.matchWork(workA)
            val eventualResultB = workMatcher.matchWork(workB)

            val eventualResults = for {
              resultA <- eventualResultA recoverWith {
                case t: Throwable =>
                  Future.successful(t)
              }
              resultB <- eventualResultB recoverWith {
                case t: Throwable =>
                  Future.successful(t)
              }
            } yield (resultA, resultB)

            whenReady(eventualResults) {
              results =>
                val resultsList = results.productIterator.toList
                val failure = resultsList.collect {
                  case t: Throwable => t
                }
                val result = resultsList.collect {
                  case r: MatcherResult => r
                }

                failure.size shouldBe 1
                result.size shouldBe 1

                lockDao.locks shouldBe empty
            }
        }
    }
  }
}
