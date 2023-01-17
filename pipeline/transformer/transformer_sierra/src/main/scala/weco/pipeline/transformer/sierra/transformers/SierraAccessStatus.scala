package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.AccessStatus
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.VarField

object SierraAccessStatus extends SierraQueryOps {
  def forBib(
    bibId: SierraBibNumber,
    bibData: SierraBibData
  ): Option[AccessStatus] = {
    val statuses =
      bibData
        .varfieldsWithTag("506")
        .flatMap { varField =>
          val terms = getTerms(varField)
          val termsStatus = statusFromTerms(terms)

          getAccessStatus(bibId, varField, termsStatus)
        }
        .distinct

    statuses match {
      case Seq(status) => Some(status)
      case _           => None
    }
  }

  // MARC 506 subfield ǂa contains "terms governing access".  This is a
  // non-repeatable field.  See https://www.loc.gov/marc/bibliographic/bd506.html
  private def getTerms(varfield: VarField): Option[String] =
    varfield
      .nonrepeatableSubfieldWithTag("a")
      .map { _.content.trim }
      .filter { _.nonEmpty }

  private def statusFromTerms(terms: Option[String]): Option[AccessStatus] =
    terms.flatMap { statusFromString }

  // Given a string value, work out what the AccessStatus should be.
  private def statusFromString(status: String): Option[AccessStatus] = {
    val normalisedStatus = status.trim.stripSuffix(".").trim.toLowerCase()

    normalisedStatus match {
      case value if value == "open with advisory" =>
        Some(AccessStatus.OpenWithAdvisory)

      // This has to come after the "OpenWithAdvisory" branch so we don't
      // match on the partial open.
      case value
          if value == "open" || value == "unrestricted" || value == "unrestricted / open" || value == "unrestricted (open)" || value == "open access" =>
        Some(AccessStatus.Open)

      case value
          if value == "restricted" || value == "certain restrictions apply" || value
            .startsWith("restricted access") =>
        Some(AccessStatus.Restricted)

      case value if value.startsWith("by appointment") =>
        Some(AccessStatus.ByAppointment)

      case value if value == "closed" =>
        Some(AccessStatus.Closed)

      case value
          if value == "cannot be produced" || value == "missing" || value == "deaccessioned" =>
        Some(AccessStatus.Unavailable)

      case value if value == "temporarily unavailable" =>
        Some(AccessStatus.TemporarilyUnavailable)

      case value
          if value == "donor permission" || value == "permission is required to view these item" || value == "permission is required to view this item" =>
        Some(AccessStatus.PermissionRequired)

      case _ =>
        warn(s"Unable to map string to access status: $status")
        None
    }
  }

  implicit class StringOps(s: String) {
    def startsWith(prefixes: String*): Boolean =
      prefixes.exists { s.startsWith }
  }

  // Get an AccessStatus that draws from our list of types.
  //
  // Rules:
  //  - if the first indicator is 0, then there are no restrictions
  //  - look in subfield ǂf for the standardised terminology
  //  - look at the "terms governing access" from 506 ǂa
  //
  // See https://www.loc.gov/marc/bibliographic/bd506.html
  private def getAccessStatus(
    bibId: SierraBibNumber,
    varfield: VarField,
    termsStatus: Option[AccessStatus]
  ): Option[AccessStatus] = {

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
        .flatMap { statusFromString }

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
