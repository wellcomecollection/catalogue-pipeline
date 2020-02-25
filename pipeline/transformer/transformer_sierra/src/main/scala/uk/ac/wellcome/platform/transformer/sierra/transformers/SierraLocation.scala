package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  SierraQueryOps,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation

trait SierraLocation extends SierraQueryOps {

  def getPhysicalLocation(itemData: SierraItemData,
                          bibData: SierraBibData): Option[PhysicalLocation] =
    itemData.location.flatMap {
      // We've seen records where the "location" field is populated in
      // the JSON, but the code and name are both empty strings or "none".
      // We can't do anything useful with this, so don't return a location.
      case SierraSourceLocation("", "")         => None
      case SierraSourceLocation("none", "none") => None
      case SierraSourceLocation(code, name) =>
        Some(
          PhysicalLocation(
            locationType = LocationType(code),
            accessConditions = getAccessConditions(bibData),
            label = name
          )
        )
    }

  def getDigitalLocation(identifier: String): DigitalLocation = {
    // This is a defensive check, it may not be needed since an identifier should always be present.
    if (!identifier.isEmpty) {
      DigitalLocation(
        url = s"https://wellcomelibrary.org/iiif/$identifier/manifest",
        license = None,
        locationType = LocationType("iiif-presentation")
      )
    } else {
      throw SierraTransformerException(
        "id required by DigitalLocation has not been provided")
    }
  }

  private def getAccessConditions(
    bibData: SierraBibData): List[AccessCondition] =
    bibData
      .varfieldsWithTag("506")
      .map { varfield =>
        AccessCondition(
          status = getAccessStatus(varfield),
          terms = varfield.subfieldsWithTag("a").contentString,
          to = varfield.subfieldsWithTag("g").contents.headOption
        )
      }
      .filter {
        case AccessCondition(None, None, None) => false
        case _                                 => true
      }

  private def getAccessStatus(varfield: VarField): Option[AccessStatus] = {
    if (varfield.indicator1 == Some("0"))
      Some(AccessStatus.Open)
    else
      varfield
        .subfieldsWithTag("f")
        .contents
        .headOption
        .map {
          case status if status.startsWith("Open with advisory") =>
            AccessStatus.OpenWithAdvisory
          case status if status.startsWith("Open") =>
            AccessStatus.Open
          case status if status.startsWith("Restricted") =>
            AccessStatus.Restricted
          case status if status.startsWith("Cannot Be Produced") =>
            AccessStatus.Restricted
          case status if status.startsWith("Certain restrictions apply") =>
            AccessStatus.Restricted
          case status if status.startsWith("Closed") =>
            AccessStatus.Closed
          case status if status.startsWith("Missing") =>
            AccessStatus.Unavailable
          case status if status.startsWith("Temporarily Unavailable") =>
            AccessStatus.Unavailable
          case status if status.startsWith("Permission Required") =>
            AccessStatus.PermissionRequired
        }
  }
}
