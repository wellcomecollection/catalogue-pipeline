package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.transformer.calm.exceptions.TransformerException.FieldMissingTransformerException
import uk.ac.wellcome.platform.transformer.calm.models.{
  CalmIdentifier,
  CalmSourceData
}

object CalmOtherIdentifiers extends CalmFieldTransformer {
  type Output = List[SourceIdentifier]

  def transform(calmTransformable: CalmSourceData) = {
    List(
      calmTransformable.altRefNo.map(CalmIdentifier.altRefNo),
      Some(CalmIdentifier.refNo(calmTransformable.refNo))) flatten match {
      case Nil  => Left(FieldMissingTransformerException)
      case list => Right(list)
    }
  }
}
