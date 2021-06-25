package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.source_model.sierra.SierraBibData
import weco.catalogue.source_model.sierra.source.{
  SierraMaterialType,
  SierraQueryOps
}

object SierraIconographicNumber
    extends SierraDataTransformer
    with SierraQueryOps {
  override type Output = Option[String]

  private val IconographicNumberMatch = "^([0-9]+i)$".r

  override def apply(bibData: SierraBibData): Option[String] =
    bibData match {
      case _ if bibData.isVisualCollections =>
        bibData
          .varfieldsWithTag("001")
          .flatMap { _.content }
          .collectFirst {
            // There are a handful of cases where the value in this field doesn't
            // look like an i-number, in which case we discard it.
            case IconographicNumberMatch(number) => number
          }

      case _ => None
    }

  private implicit class VisualCollectionOps(bibData: SierraBibData) {
    def isVisualCollections: Boolean =
      bibData.materialType match {
        case Some(SierraMaterialType("k")) => true // Pictures
        case Some(SierraMaterialType("r")) => true // 3D-Objects
        case _                             => false
      }
  }
}
