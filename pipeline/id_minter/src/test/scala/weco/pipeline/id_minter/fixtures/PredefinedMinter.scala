package weco.pipeline.id_minter.fixtures

import io.circe.Json
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}
import weco.pipeline.id_minter.{MultiIdMinter, SingleDocumentIdMinter}
import weco.pipeline.id_minter.models.Identifier
import weco.pipeline.id_minter.steps.CanonicalIdentifierGenerator
import weco.pipeline_storage.Retriever

import scala.util.{Failure, Success, Try}

trait PredefinedMinter {

  case class MockGenerator(ids: Map[SourceIdentifier, String])
      extends CanonicalIdentifierGenerator {
    override def retrieveOrGenerateCanonicalIds(
      sourceIdentifiers: Seq[SourceIdentifier]
    ): Try[Map[SourceIdentifier, Identifier]] = {

      val found = ids.filter {
        case (k, _) => sourceIdentifiers.contains(k)
      } map {
        case (k, v) =>
          k -> Identifier(
            canonicalId = CanonicalId(v),
            sourceIdentifier = k
          )
      }

      val notFound = sourceIdentifiers.toSet -- found.keySet
      notFound match {
        case s if s.isEmpty => Success(found)
        case _              => Failure(new Exception("Unlucky"))
      }
    }
  }

  protected def singleMinter(idMap: Map[SourceIdentifier, String]) =
    new SingleDocumentIdMinter(
      MockGenerator(idMap)
    )
  protected def multiMinter(
    retriever: Retriever[Json],
    idMap: Map[SourceIdentifier, String]
  ) = new MultiIdMinter(retriever, singleMinter(idMap))
}
