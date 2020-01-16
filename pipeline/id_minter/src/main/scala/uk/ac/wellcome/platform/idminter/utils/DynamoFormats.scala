package uk.ac.wellcome.platform.idminter.utils

import org.scanamo.DynamoFormat
import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}

object DynamoFormats {
  implicit val sourceIdentifier: DynamoFormat[SourceIdentifier] = DynamoFormat
    .coercedXmap[SourceIdentifier, String, IllegalArgumentException] { value =>
      value.split("/", 3) match {
        case Array(identifierType, ontologyType, value) =>
          SourceIdentifier(
            IdentifierType(identifierType),
            ontologyType,
            value
          )

        case _ =>
          throw new IllegalArgumentException(
            s"Cannot create bag ID from $value")
      }
    }(_.createId)
}
