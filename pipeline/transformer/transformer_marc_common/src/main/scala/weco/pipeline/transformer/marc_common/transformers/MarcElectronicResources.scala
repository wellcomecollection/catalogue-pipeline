package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation
}
import weco.catalogue.internal_model.locations.LocationType.OnlineResource
import weco.catalogue.internal_model.work.Item
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcRecord,
  MarcSubfield
}

import java.net.URL
import scala.util.{Failure, Success, Try}

trait MarcElectronicResources extends Logging {

  protected def toItems(
    record: MarcRecord
  )(implicit ctx: LoggingContext): Seq[Item[IdState.Unminted]] =
    record
      .fieldsWithTags("856")
      .flatMap(field => toItem(field))

  protected def toItem(
    field: MarcField
  )(implicit ctx: LoggingContext): Option[Item[IdState.Unminted]] =
    getUrl(field) match {
      case Failure(exception) =>
        warn(ctx(exception.getMessage))
        None
      case Success(url) =>
        val (title, linkText) = getTitleAndLinkText(getLabel(field))
        Some(toItem(url, status(field), title, linkText))
    }

  protected def getLabel(field: MarcField): String =
    field.subfields.filter(_.tag == "y").mkString(" ")

  protected def getTitleAndLinkText(
    label: String
  ): (Option[String], Option[String]) = (Some(label), None)

  // We take the URL from subfield ǂu.  If subfield ǂu is missing, repeated,
  // or contains something other than a URL, we discard it.
  private def getUrl(
    field: MarcField
  ): Try[String] =
    field.subfields.filter(_.tag == "u") match {
      case Seq(MarcSubfield(_, content)) if isUrl(content) => Success(content)

      case Seq(MarcSubfield(_, content)) =>
        Failure(
          new Exception(
            s"has a value in 856 ǂu which isn't a URL: $content"
          )
        )

      case Nil =>
        Failure(new Exception(s"has a field 856 without any URLs"))

      case _ =>
        Failure(new Exception(s"has a field 856 with repeated subfield ǂu"))
    }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess

  // 856 indicator 2 takes the following values:
  //
  //      Relationship
  //      # - No information provided
  //      0 - Resource
  //      1 - Version of resource
  //      2 - Related resource
  //      8 - No display constant generated
  //
  // This allows us to include URLs that are related to the work, but not the
  // work itself (e.g. a description on a publisher website).
  private def status(field: MarcField): LicensedResources =
    field.indicator2 match {
      case "2" =>
        AccessStatus.LicensedResources(
          relationship = LicensedResources.RelatedResource
        )
      case _ =>
        AccessStatus.LicensedResources(
          relationship = LicensedResources.Resource
        )
    }

  private def toItem(
    url: String,
    status: LicensedResources,
    title: Option[String],
    linkText: Option[String]
  ): Item[IdState.Unminted] = Item(
    title = title,
    locations = List(
      DigitalLocation(
        url = url,
        linkText = linkText,
        locationType = OnlineResource,
        // We want these works to show up in a filter for "available online",
        // so we need to add an access status.
        //
        // Neither "Open" nor "Open with advisory" are appropriate.
        //
        // See https://github.com/wellcomecollection/platform/issues/5062 for
        // more discussion and conversations about this.
        accessConditions = List(
          AccessCondition(
            method = AccessMethod.ViewOnline,
            status = status
          )
        )
      )
    )
  )
}

object MarcElectronicResources extends MarcElectronicResources {
  def apply(record: MarcRecord): Seq[Item[IdState.Unminted]] = {
    implicit val ctx: LoggingContext = new LoggingContext("")
    toItems(record)
  }

}
