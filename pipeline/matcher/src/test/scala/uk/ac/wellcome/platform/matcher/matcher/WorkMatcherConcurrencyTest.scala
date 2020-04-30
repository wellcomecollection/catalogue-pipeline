package uk.ac.wellcome.platform.matcher.matcher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.models.matcher.MatcherResult
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.MergeCandidate
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.storage.locking.dynamo.{
  DynamoLockingService,
  ExpiringLock
}

class WorkMatcherConcurrencyTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with MockitoSugar
    with WorksGenerators {

  it("processes one of two conflicting concurrent updates and locks the other") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withLockDao(dynamoClient, lockTable) { implicit lockDao =>
            withWorkMatcherAndLockingService(
              workGraphStore,
              new DynamoLockingService) { workMatcher =>
              val identifierA =
                createSierraSystemSourceIdentifierWith(value = "A")
              val identifierB =
                createSierraSystemSourceIdentifierWith(value = "B")

              val workA = createUnidentifiedWorkWith(
                sourceIdentifier = identifierA,
                mergeCandidates = List(MergeCandidate(identifierB))
              )

              val workB = createUnidentifiedWorkWith(
                sourceIdentifier = identifierB
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

                scan[ExpiringLock](dynamoClient, lockTable.name) shouldBe empty
              }
            }
          }
        }
      }
    }
  }
}
