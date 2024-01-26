package weco.pipeline.transformer.miro.source

import io.circe.generic.extras.JsonKey
import org.apache.commons.text.StringEscapeUtils
import weco.json.JsonUtil.fromJson
import weco.pipeline.transformer.miro.Implicits._

import scala.util.{Failure, Success}

case class MiroRecord(
  @JsonKey("image_title") title: Option[String] = None,
  // Miro data has null json values scattered a bit everywhere.
  // If the type is not Option, circe decoding fails when it
  // encounters a null. Hence this weird type signature
  @JsonKey("image_creator") creator: Option[List[Option[String]]] = None,
  @JsonKey("image_image_desc") description: Option[String] = None,
  @JsonKey("image_image_desc_academic") academicDescription: Option[String] = None,
  @JsonKey("image_secondary_creator") secondaryCreator: Option[List[String]] = None,
  @JsonKey("image_artwork_date") artworkDate: Option[String] = None,
  @JsonKey("image_cleared") cleared: Option[String] = None,
  @JsonKey("image_copyright_cleared") copyrightCleared: Option[String] = None,
  @JsonKey("image_keywords") keywords: Option[List[String]] = None,
  @JsonKey("image_keywords_unauth") keywordsUnauth: Option[
    List[Option[String]]
  ] = None,
  @JsonKey("image_phys_format") physFormat: Option[String] = None,
  @JsonKey("image_lc_genre") lcGenre: Option[String] = None,
  @JsonKey("image_tech_file_size") techFileSize: Option[List[String]] = None,
  @JsonKey("image_use_restrictions") useRestrictions: Option[String] = None,
  @JsonKey("image_supp_lettering") suppLettering: Option[String] = None,
  @JsonKey("image_innopac_id") innopacID: Option[String] = None,
  @JsonKey("image_credit_line") creditLine: Option[String] = None,
  @JsonKey("image_source_code") sourceCode: Option[String] = None,
  @JsonKey("image_library_ref_department") libraryRefDepartment: List[
    Option[String]
  ] = Nil,
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

  /* Some of the Unicode characters got mangled in the Miro exports, which
   * corrupt names like:
   *
   *    Adêle Mongrédien
   *    Hermann von Schlagintweit-Sakünlünski
   *    Écorché horse
   *    Félix Méheux
   *    André Just
   *
   * This presents as ugly nonsense in the API.
   *
   * Because we can't edit the Miro data, we have to fix this Unicode mangling
   * in the transformer.  I ran the transformed Works through a Python library
   * ftfy ("fixes text for you"), which is designed to detect and fix this sort
   * of mojibake.  I'm hard-coding the substitutions rather than running something
   * like that on every transform so I don't introduce more problems than I solve.
   *
   * The long-term fix is for these Miro records to get paired with Sierra records
   * with the correct data.
   */
  private def fixBadUnicode(s: String): String =
    s.replaceAll(" \\u00cc\\u0081", " ́")
      .replaceAll("i\\u00cc\\u00a8", "į")
      .replaceAll("\\u00c3&#8224;", "Æ")
      .replaceAll("\\u00c3&#8240;", "É")
      .replaceAll("\\u00c3\\u00a0", "à")
      .replaceAll("\\u00c3\\u00a1", "á")
      .replaceAll("\\u00c3\\u00a2", "â")
      .replaceAll("\\u00c3\\u00a4", "ä")
      .replaceAll("\\u00c3\\u00a6", "æ")
      .replaceAll("\\u00c3\\u00a7", "ç")
      .replaceAll("\\u00c3\\u00a8", "è")
      .replaceAll("\\u00c3\\u00a9", "é")
      .replaceAll("\\u00c3\\u00aa", "ê")
      .replaceAll("\\u00c3\\u00ab", "ë")
      .replaceAll("\\u00c3\\u00ad", "í")
      .replaceAll("\\u00c3\\u00b3", "ó")
      .replaceAll("\\u00c3\\u00b4", "ô")
      .replaceAll("\\u00c3\\u00b6", "ö")
      .replaceAll("\\u00c3\\u00ba", "ú")
      .replaceAll("\\u00c3\\u00bb", "û")
      .replaceAll("\\u00c3\\u00bc", "ü")
      .replaceAll("\\u00c4&#402;", "ă")
      .replaceAll("\\u00c4\\u0081", "ā")
      .replaceAll("\\u00c4\\u008d", "č")
      .replaceAll("\\u00c5&#376;", "ş")
      .replaceAll("\\u00c5&#8220;", "œ")
      .replaceAll("\\u00c5\\u008d", "ō")
      .replaceAll("\\u00c5\\u008f", "ŏ")
      .replaceAll("\\u00c5\\u00a3", "ţ")
      .replaceAll("\\u00c5\\u00ab", "ū")
      .replaceAll("\\u00cc\\u0081", " ́")
      .replaceAll("\\u00e1\\u00b8\\u00a7", "ḧ")
      .replaceAll("\\u00e2\\u20ac\\u02dc", "‘")
      .replaceAll("\\u00e2\\u20ac\\u2122", "’")

  def create(jsonString: String): MiroRecord = {
    val unescapedData = fixBadUnicode(unescapeHtml(jsonString))

    fromJson[MiroRecord](unescapedData) match {
      case Success(miroRecord) => miroRecord
      case Failure(e)          => throw e
    }
  }
}
