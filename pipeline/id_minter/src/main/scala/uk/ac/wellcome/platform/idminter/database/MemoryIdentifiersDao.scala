package uk.ac.wellcome.platform.idminter.database
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.storage.{DaoWriteError, DoesNotExistError}

class MemoryIdentifiersDao extends IdentifiersDao {
  var identifiers: Seq[Identifier] = Seq.empty

  override def get(sourceIdentifier: SourceIdentifier): GetResult = {
    val candidates =
      identifiers
        .filter { _.OntologyType == sourceIdentifier.ontologyType }
        .filter { _.SourceSystem == sourceIdentifier.identifierType.id }
        .filter { _.SourceId == sourceIdentifier.value }

    candidates match {
      case Seq(identifier) => Right(identifier)
      case _ => Left(DoesNotExistError(
        new Throwable(s"Nothing matches $sourceIdentifier"))
      )
    }
  }

  override def put(newIdentifier: Identifier): PutResult = {
    val conflicts =
      identifiers.filter { existing =>
        existing.OntologyType == newIdentifier.OntologyType ||
        existing.SourceSystem == newIdentifier.SourceSystem ||
        existing.SourceId == newIdentifier.SourceId ||
        existing.CanonicalId == newIdentifier.CanonicalId
      }

    conflicts match {
      case Nil =>
        identifiers = identifiers :+ newIdentifier
        Right(())
      case _ =>
        Left(DaoWriteError(
          new Throwable(s"New identifier $newIdentifier has conflicts: $conflicts")
        ))
    }
  }
}
