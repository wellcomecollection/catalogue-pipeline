package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.fields.SierraMaterialType

object SierraIconographicNumber
    extends SierraDataTransformer
    with SierraQueryOps {
  override type Output = Option[String]

  // This expression could be made slightly more exclusive.
  // In practice, an i-number is between 1 and 8 digits before the `i`,
  // and between 1 and 3 after the `.` if present.
  // However, it is preferable to be slightly more permissive than necessary here.
  // This will match any string that a human author would immediately recognise as an i-number
  // and reject those that are obviously not.
  // If a new i-number is coined with 9 leading digits, or four trailing ones, this will still work.
  private val IconographicNumberMatch = "^([0-9]+i(\\.[0-9]+)?)$".r

  override def apply(bibData: SierraBibData): Option[String] =
    bibData match {
      case _ if bibData.isVisualCollections =>
        bibData
          .varfieldsWithTag("001")
          .flatMap { _.content }
          .collectFirst {
            // There are a handful of cases where the value in this field doesn't
            // look like an i-number, in which case we discard it.
            case IconographicNumberMatch(number, _) => number
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
