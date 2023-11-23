package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.locations.License
import weco.pipeline.transformer.result.Result
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase

object MetsLicence {
  def apply(
    dz: Option[String]
  ): Result[Option[License]] =
    dz match {
      case None => Right(None)
      case Some(s) =>
        s match {
          // A lot of METS record have "Copyright not cleared"
          // or "rightsstatements.org/page/InC/1.0/?language=en" as dz access condition.
          // They both need to be mapped to a InCopyright license so hardcoding here
          //
          // Discussion about whether it's okay to map "all rights reserved" to
          // "in copyright": https://wellcome.slack.com/archives/CBT40CMKQ/p1621243064241400
          case s if s.toLowerCase() == "copyright not cleared" =>
            Right(Some(License.InCopyright))
          case s if s == "rightsstatements.org/page/InC/1.0/?language=en" =>
            Right(Some(License.InCopyright))
          case s if s.toLowerCase == "all rights reserved" =>
            Right(Some(License.InCopyright))

          // The access conditions in mets contains sometimes the license id (lowercase),
          // sometimes the label (ie "in copyright")
          // and sometimes the url of the license
          case accessCondition =>
            License.values.find {
              license =>
                equalsIgnoreCase(
                  license.id,
                  accessCondition
                ) || equalsIgnoreCase(
                  license.label,
                  accessCondition
                ) || license.url.equals(accessCondition)

            } match {
              case Some(license) => Right(Some(license))
              case None =>
                Left(
                  new Exception(s"Couldn't match $accessCondition to a license")
                )
            }
        }
    }
}
