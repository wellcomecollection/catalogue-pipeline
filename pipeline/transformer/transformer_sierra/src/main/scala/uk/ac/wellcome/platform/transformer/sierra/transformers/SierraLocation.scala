package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  SierraQueryOps,
  VarField
}
import weco.catalogue.sierra_adapter.models.{SierraBibNumber, SierraItemNumber}

trait SierraLocation extends SierraQueryOps with Logging {

  def getPhysicalLocation(
    bibNumber: SierraBibNumber,
    itemNumber: SierraItemNumber,
    itemData: SierraItemData,
    bibData: SierraBibData,
    fallbackLocation: Option[(PhysicalLocationType, String)] = None)
    : Option[PhysicalLocation] =
    for {
      sourceLocation <- itemData.location

      (locationType, label) <- {
        val parsedLocationType =
          SierraPhysicalLocationType.fromName(itemNumber, sourceLocation.name)

        (parsedLocationType, fallbackLocation) match {
          case (Some(locationType), _) =>
            val label = locationType match {
              case LocationType.ClosedStores => LocationType.ClosedStores.label
              case _                         => sourceLocation.name
            }

            Some((locationType, label))

          case (_, Some(fallbackLocation)) =>
            Some(fallbackLocation)

          case _ => None
        }
      }

      physicalLocation = PhysicalLocation(
        locationType = locationType,
        accessConditions = getAccessConditions(bibNumber, bibData),
        label = label,
        // This is meant to be a "good enough" implementation of a shelfmark.
        // We may revisit this in future, and populate it directly from the
        // MARC fields if we want to be more picky about our rules.
        shelfmark = itemData.callNumber
      )
    } yield physicalLocation

  private def getAccessConditions(
    bibId: SierraBibNumber,
    bibData: SierraBibData): List[AccessCondition] =
    bibData
      .varfieldsWithTag("506")
      .map { varfield =>
        // MARC 506 subfield ǂa contains "terms governing access".  This is a
        // non-repeatable field.  See https://www.loc.gov/marc/bibliographic/bd506.html
        val terms = varfield
          .nonrepeatableSubfieldWithTag("a")
          .map { _.content.trim }

        AccessCondition(
          status = getAccessStatus(bibId, varfield, terms),
          terms = terms,
          to = varfield.subfieldsWithTag("g").contents.headOption
        )
      }
      .filter {
        case AccessCondition(None, None, None) => false
        case _                                 => true
      }

  // Get an AccessStatus that draws from our list of types.
  //
  // Rules:
  //  - if the first indicator is 0, then there are no restrictions
  //  - look in subfield ǂf for the standardised terminology
  //  - look at the "terms governing access" from 506 ǂa
  //
  // See https://www.loc.gov/marc/bibliographic/bd506.html
  private def getAccessStatus(bibId: SierraBibNumber,
                              varfield: VarField,
                              terms: Option[String]): Option[AccessStatus] = {

    // If the first indicator is 0, then there are no restrictions
    val indicator0 =
      if (varfield.indicator1.contains("0"))
        Some(AccessStatus.Open)
      else
        None

    // Look in subfield ǂf for the standardised terminology
    val subfieldF =
      varfield
        .subfieldsWithTag("f")
        .contents
        .headOption
        .flatMap { contents =>
          AccessStatus(contents) match {
            case Right(status) => Some(status)
            case Left(err) =>
              warn(
                s"$bibId: Unable to parse access status from subfield ǂf: $contents ($err)")
              None
          }
        }

    // Look at the terms for the standardised terminology
    val termsStatus =
      terms.map { AccessStatus(_) }.collect { case Right(status) => status }

    // Finally, we look at all three fields together.  If the data is inconsistent
    // we should drop a warning and not set an access status, rather than set one that's
    // wrong.  This presumes that:
    //
    //  1. The data in the "terms" field is more likely to be accurate
    //  2. Sins of omission (skipping the field) are better than sins of commission
    //     (e.g. claiming an Item is open when it's actually restricted)
    //
    Seq(indicator0, subfieldF, termsStatus).flatten.distinct match {
      case Nil         => None
      case Seq(status) => Some(status)
      case multiple =>
        warn(s"$bibId: Multiple, conflicting access statuses: $multiple")
        None
    }
  }
}
