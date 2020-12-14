package uk.ac.wellcome.platform.matcher.matcher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.matcher.MatcherResult
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.{IdState, MergeCandidate}
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.storage.locking.dynamo.{DynamoLockingService, ExpiringLock}

class WorkMatcherConcurrencyTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with WorkGenerators {

  it("processes one of two conflicting concurrent updates and locks the other") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withLockDao(dynamoClient, lockTable) { implicit lockDao =>
            withWorkMatcherAndLockingService(
              workGraphStore,
              new DynamoLockingService) { workMatcher =>
              val identifierA = IdState.Identified(createCanonicalId, createSierraSystemSourceIdentifierWith(value = "A"))
              val identifierB = IdState.Identified(createCanonicalId, createSierraSystemSourceIdentifierWith(value = "B"))

              val workA = identifiedWork(canonicalId = identifierA.canonicalId, sourceIdentifier = identifierA.sourceIdentifier)
                .mergeCandidates(List(MergeCandidate(identifierB)))

              val workB = identifiedWork(canonicalId = identifierB.canonicalId, sourceIdentifier = identifierB.sourceIdentifier)

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
