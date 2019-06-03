package uk.ac.wellcome.platform.matcher.fixtures

import java.util.UUID

import uk.ac.wellcome.models.matcher.MatchedIdentifiers
import uk.ac.wellcome.storage.{LockDao, LockingService}
import uk.ac.wellcome.storage.memory.MemoryLockDao

import scala.concurrent.Future

trait LockingFixtures {
  type MatcherLockDao = MemoryLockDao[String, UUID]
  type MatcherLockingService =
    LockingService[Set[MatchedIdentifiers], Future, LockDao[String, UUID]]

  def createLockDao: MatcherLockDao =
    new MatcherLockDao {}

  def createLockingService(
    dao: MatcherLockDao = createLockDao): MatcherLockingService =
    new MatcherLockingService {
      override implicit val lockDao: LockDao[String, UUID] = dao

      override protected def createContextId(): lockDao.ContextId =
        UUID.randomUUID()
    }
}
