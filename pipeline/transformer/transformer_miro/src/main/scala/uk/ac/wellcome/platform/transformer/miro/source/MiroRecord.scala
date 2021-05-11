package uk.ac.wellcome.platform.transformer.miro.source

import io.circe.generic.extras.JsonKey
import org.apache.commons.text.StringEscapeUtils
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Success}

case class MiroRecord(
  @JsonKey("image_title") title: Option[String] = None,
  // Miro data has null json values scattered a bit everywhere.
  // If the type is not Option, circe decoding fails when it
  // encounters a null. Hence this weird type signature
  @JsonKey("image_creator") creator: Option[List[Option[String]]] = None,
  @JsonKey("image_image_desc") description: Option[String] = None,
  @JsonKey("image_image_desc_academic") academicDescription: Option[String] =
    None,
  @JsonKey("image_secondary_creator") secondaryCreator: Option[List[String]] =
    None,
  @JsonKey("image_artwork_date") artworkDate: Option[String] = None,
  @JsonKey("image_cleared") cleared: Option[String] = None,
  @JsonKey("image_copyright_cleared") copyrightCleared: Option[String] = None,
  @JsonKey("image_keywords") keywords: Option[List[String]] = None,
  @JsonKey("image_keywords_unauth") keywordsUnauth: Option[
    List[Option[String]]] = None,
  @JsonKey("image_phys_format") physFormat: Option[String] = None,
  @JsonKey("image_lc_genre") lcGenre: Option[String] = None,
  @JsonKey("image_tech_file_size") techFileSize: Option[List[String]] = None,
  @JsonKey("image_use_restrictions") useRestrictions: Option[String] = None,
  @JsonKey("image_supp_lettering") suppLettering: Option[String] = None,
  @JsonKey("image_innopac_id") innopacID: Option[String] = None,
  @JsonKey("image_credit_line") creditLine: Option[String] = None,
  @JsonKey("image_source_code") sourceCode: Option[String] = None,
  @JsonKey("image_library_ref_department") libraryRefDepartment: List[
    Option[String]] = Nil,
  @JsonKey("image_library_ref_id") libraryRefId: List[Option[String]] = Nil,
  @JsonKey("image_award") award: List[Option[String]] = Nil,
  @JsonKey("image_award_date") awardDate: List[Option[String]] = Nil,
  @JsonKey("image_no_calc") imageNumber: String
)

case object MiroRecord {

  /* Some of the Miro fields were imported from Sierra, and had special
   * characters replaced by HTML-encoded entities when copied across.
   *
   * We need to fix them up before we decode as JSON.
   */
  private def unescapeHtml(s: String): String =
    StringEscapeUtils.unescapeHtml3(s)

  /* Fix a couple of names that got mangled in the Miro exports:
   *
   *    Adêle Mongrédien
   *    Hermann von Schlagintweit-Sakünlünski
   *
   * This presents as ugly nonsense in the API.
   *
   * These checks are deliberately conservative to avoid introducing more
   * problems than they fix.  The long-term fix is for these Miro records to
   * get paired with Sierra records with the correct data.
   */
  private def fixBadUnicode(s: String): String =
    s.replaceAll("Ad\\u00c3\\u00aale", "Adêle")
      .replaceAll("Mongr\\u00c3\\u00a9dien", "Mongrédien")
      .replaceAll("Sak\\u00c3\\u00bcnl\\u00c3\\u00bcnski", "Sakünlünski")

  def create(jsonString: String): MiroRecord = {
    val unescapedData = fixBadUnicode(unescapeHtml(jsonString))

    fromJson[MiroRecord](unescapedData) match {
      case Success(miroRecord) => miroRecord
      case Failure(e)          => throw e
    }
  }
}
