package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.platform.transformer.sierra.source.{SierraBibData, SierraItemData, SierraQueryOps, VarField}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation

trait SierraLocation extends SierraQueryOps {

  def getPhysicalLocation(itemData: SierraItemData, bibData: SierraBibData): Option[PhysicalLocation] =
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
            accessConditions = Option(getAccessConditions(bibData)).filter(_.nonEmpty),
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

  private def getAccessConditions(bibData: SierraBibData): List[AccessCondition] =
    bibData.varfieldsWithTag("506").map { varfield =>
      AccessCondition(
        status = getAccessStatus(varfield),
        terms = varfield.subfieldsWithTag("a").contentString,
        to = varfield.subfieldsWithTag("g").contents.headOption
      )
    }

  private def getAccessStatus(varfield: VarField): AccessStatus =
    if (varfield.indicator1 == Some("0"))
      AccessStatus.Open
    else
      varfield
        .subfieldsWithTag("f")
        .contents
        .headOption
        .map {
          case "Open"               => AccessStatus.Open
          case "Open with advisory" => AccessStatus.OpenWithAdvisory
          case "Restricted"         => AccessStatus.Restricted
          case "Closed"             => AccessStatus.Closed
          case status =>
            throw new Exception(s"Unrecognised AccessStatus: $status")
        }
        .getOrElse {
          throw new Exception("Could not parse AccessCondition: 506$f not found")
        }
}
