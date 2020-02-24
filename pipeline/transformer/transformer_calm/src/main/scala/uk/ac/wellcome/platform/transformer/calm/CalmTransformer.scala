package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.models.work.internal.{TransformedBaseWork, UnidentifiedWork, WorkData}
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.platform.transformer.calm.transformers.CalmToSourceIdentifier

trait CalmTransformerError extends Throwable
case object SourceIdentifierMissingError extends CalmTransformerError
case object MultipleSourceIdentifiersFoundError extends CalmTransformerError

object CalmTransformer extends Transformer[CalmRecord, TransformedBaseWork] {
  def transform(calmRecord: CalmRecord)
  : Either[CalmTransformerError, TransformedBaseWork] = {
    val sourceIdentifier = CalmToSourceIdentifier.transform(calmRecord)

    sourceIdentifier.map {
      case head :: Nil =>
        Right(
          UnidentifiedWork(
            version = 1,
            sourceIdentifier = head,
            data = WorkData()))

      case _ => Left(MultipleSourceIdentifiersFoundError)
    } getOrElse Left(SourceIdentifierMissingError)

  }

}
