package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema

import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal.IdentifiedWork

@Schema(
  name = "Work",
  description =
    "An individual work such as a text, archive item or picture; or a grouping of individual works (so, for instance, an archive collection counts as a work, as do all the series and individual files within it).  Each work may exist in multiple instances (e.g. copies of the same book).  N.B. this is not synonymous with \\\"work\\\" as that is understood in the International Federation of Library Associations and Institutions' Functional Requirements for Bibliographic Records model (FRBR) but represents something lower down the FRBR hierarchy, namely manifestation. Groups of related items are also included as works because they have similar properties to the individual ones."
)
case class DisplayWorkV2(
  @Schema(
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: String,
  @Schema(
    `type` = "String",
    description =
      "The title or other short label of a work, including labels not present in the actual work or item but applied by the cataloguer for the purposes of search or description."
  ) title: Option[String],
  @Schema(
    `type` = "List[String]",
    description = "Alternative titles of  the work."
  ) alternativeTitles: List[String],
  @Schema(
    `type` = "String",
    description = "A description given to a thing."
  ) description: Option[String] = None,
  @Schema(
    `type` = "String",
    description =
      "A description of specific physical characteristics of the work."
  ) physicalDescription: Option[String] = None,
  @Schema(
    `type` = "uk.ac.wellcome.display.models.DisplayWorkType",
    description = "The type of work."
  ) workType: Option[DisplayWorkType] = None,
  @Schema(
    `type` = "String",
    description = "Recording written text on a (usually visual) work."
  ) lettering: Option[String] = None,
  @Schema(
    `type` = "uk.ac.wellcome.display.models.v2.DisplayPeriod",
    description =
      "Relates the creation of a work to a date, when the date of creation does not cover a range."
  ) createdDate: Option[DisplayPeriod] = None,
  @Schema(
    description =
      "Relates a work to its author, compiler, editor, artist or other entity responsible for its coming into existence in the form that it has."
  ) contributors: Option[List[DisplayContributor]] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @Schema(
    description =
      "Relates a work to the general thesaurus-based concept that describes the work's content."
  ) subjects: Option[List[DisplaySubject]] = None,
  @Schema(
    description =
      "Relates a work to the genre that describes the work's content."
  ) genres: Option[List[DisplayGenre]] = None,
  @Schema(
    `type` = "uk.ac.wellcome.display.models.v2.DisplayLocationV2",
    description =
      "Relates any thing to the location of a representative thumbnail image"
  ) thumbnail: Option[DisplayLocationV2] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayItemV2]",
    description = "List of items related to this work."
  ) items: Option[List[DisplayItemV2]] = None,
  @Schema(
    description = "Relates a work to its production events."
  ) production: Option[List[DisplayProductionEvent]] = None,
  @Schema(
    `type` = "uk.ac.wellcome.display.models.DisplayLanguage",
    description = "Relates a work to its primary language."
  ) language: Option[DisplayLanguage] = None,
  @Schema(
    `type` = "String",
    description = "Information relating to the edition of a work."
  ) edition: Option[String] = None,
  @Schema(
    `type` = "List[String]",
    description = "Miscellaneous notes associated with the work."
  ) notes: Option[List[DisplayNote]] = None,
  @Schema(
    `type` = "Integer",
    description = "The playing time for audiovisual works, in seconds."
  ) duration: Option[Int] = None,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Work"
) extends DisplayWork

case object DisplayWorkV2 {

  def apply(work: IdentifiedWork, includes: V2WorksIncludes): DisplayWorkV2 =
    DisplayWorkV2(
      id = work.canonicalId,
      title = work.data.title,
      alternativeTitles = work.data.alternativeTitles,
      description = work.data.description,
      physicalDescription = work.data.physicalDescription,
      lettering = work.data.lettering,
      createdDate = work.data.createdDate.map { DisplayPeriod(_) },
      contributors = if (includes.contributors) {
        Some(work.data.contributors.map {
          DisplayContributor(_, includesIdentifiers = includes.identifiers)
        })
      } else None,
      subjects = if (includes.subjects) {
        Some(work.data.subjects.map {
          DisplaySubject(_, includesIdentifiers = includes.identifiers)
        })
      } else None,
      genres = if (includes.genres) {
        Some(work.data.genres.map {
          DisplayGenre(_, includesIdentifiers = includes.identifiers)
        })
      } else None,
      identifiers =
        if (includes.identifiers)
          Some(work.identifiers.map { DisplayIdentifierV2(_) })
        else None,
      workType = work.data.workType.map { DisplayWorkType(_) },
      thumbnail = work.data.thumbnail.map { DisplayLocationV2(_) },
      items =
        if (includes.items)
          Some(work.data.items.map {
            DisplayItemV2(_, includesIdentifiers = includes.identifiers)
          })
        else None,
      production =
        if (includes.production) Some(work.data.production.map {
          DisplayProductionEvent(_, includesIdentifiers = includes.identifiers)
        })
        else None,
      language = work.data.language.map { DisplayLanguage(_) },
      edition = work.data.edition,
      notes =
        if (includes.notes)
          Some(DisplayNote.merge(work.data.notes.map(DisplayNote(_))))
        else None,
      duration = work.data.duration,
    )

  def apply(work: IdentifiedWork): DisplayWorkV2 =
    DisplayWorkV2(work = work, includes = V2WorksIncludes())
}
