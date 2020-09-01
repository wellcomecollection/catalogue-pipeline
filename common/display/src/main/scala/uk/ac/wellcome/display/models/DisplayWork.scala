package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{
  IdentifiedWork,
  RelatedWork,
  RelatedWorks
}

@Schema(
  name = "Work",
  description =
    "An individual work such as a text, archive item or picture; or a grouping of individual works (so, for instance, an archive collection counts as a work, as do all the series and individual files within it).  Each work may exist in multiple instances (e.g. copies of the same book).  N.B. this is not synonymous with \\\"work\\\" as that is understood in the International Federation of Library Associations and Institutions' Functional Requirements for Bibliographic Records model (FRBR) but represents something lower down the FRBR hierarchy, namely manifestation. Groups of related items are also included as works because they have similar properties to the individual ones."
)
case class DisplayWork(
  @Schema(
    accessMode = Schema.AccessMode.READ_ONLY,
    description = "The canonical identifier given to a thing.") id: String,
  @Schema(
    description =
      "The title or other short label of a work, including labels not present in the actual work or item but applied by the cataloguer for the purposes of search or description."
  ) title: Option[String],
  @Schema(
    `type` = "List[String]",
    description = "Alternative titles of  the work."
  ) alternativeTitles: List[String],
  @Schema(
    `type` = "String",
    description =
      "The identifier used by researchers to cite or refer to a work."
  ) referenceNumber: Option[String] = None,
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
    `type` = "uk.ac.wellcome.display.models.DisplayPeriod",
    description =
      "Relates the creation of a work to a date, when the date of creation does not cover a range."
  ) createdDate: Option[DisplayPeriod] = None,
  @Schema(
    description =
      "Relates a work to its author, compiler, editor, artist or other entity responsible for its coming into existence in the form that it has."
  ) contributors: Option[List[DisplayContributor]] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.Display.models.DisplayIdentifier]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]] = None,
  @Schema(
    description =
      "Relates a work to the general thesaurus-based concept that describes the work's content."
  ) subjects: Option[List[DisplaySubject]] = None,
  @Schema(
    description =
      "Relates a work to the genre that describes the work's content."
  ) genres: Option[List[DisplayGenre]] = None,
  @Schema(
    `type` = "uk.ac.wellcome.Display.models.DisplayLocation",
    description =
      "Relates any thing to the location of a representative thumbnail image"
  ) thumbnail: Option[DisplayLocation] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.Display.models.DisplayItem]",
    description = "List of items related to this work."
  ) items: Option[List[DisplayItem]] = None,
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
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.DisplayImageWorkInclude]",
    description = "Identifiers of images that are sourced from this work"
  ) images: Option[List[DisplayWorkImageInclude]] = None,
  @Schema(
    `type` = "List[Work]",
    description = "Child works."
  ) parts: Option[List[DisplayWork]] = None,
  @Schema(
    `type` = "List[Work]",
    description = "Ancestor works."
  ) partOf: Option[List[DisplayWork]] = None,
  @Schema(
    `type` = "List[Work]",
    description = "Sibling works earlier in a series."
  ) precededBy: Option[List[DisplayWork]] = None,
  @Schema(
    `type` = "List[Work]",
    description = "Sibling works later in a series."
  ) succeededBy: Option[List[DisplayWork]] = None,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Work"
)

case object DisplayWork {

  def apply(work: IdentifiedWork, includes: WorksIncludes): DisplayWork =
    DisplayWork(
      id = work.canonicalId,
      title = work.data.title,
      alternativeTitles = work.data.alternativeTitles,
      referenceNumber = work.data.collectionPath.flatMap(_.label),
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
          Some(work.identifiers.map { DisplayIdentifier(_) })
        else None,
      workType = work.data.workType.map { DisplayWorkType(_) },
      thumbnail = work.data.thumbnail.map { DisplayLocation(_) },
      items =
        if (includes.items)
          Some(work.data.items.map {
            DisplayItem(_, includesIdentifiers = includes.identifiers)
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
      images =
        if (includes.images)
          Some(work.data.images.map(DisplayWorkImageInclude(_)))
        else None
    )

  def apply(work: IdentifiedWork): DisplayWork =
    DisplayWork(work = work, includes = WorksIncludes())

  def apply(work: IdentifiedWork,
            includes: WorksIncludes,
            relatedWorks: RelatedWorks): DisplayWork =
    DisplayWork(work, includes).copy(
      parts =
        if (includes.parts)
          relatedWorks.parts.map { parts =>
            parts.map {
              case RelatedWork(work, related) =>
                DisplayWork(work, includes, related)
            }
          } else None,
      partOf =
        if (includes.partOf)
          relatedWorks.partOf.map { partOf =>
            partOf.map {
              case RelatedWork(work, related) =>
                DisplayWork(work, includes, related)
            }
          } else None,
      precededBy =
        if (includes.precededBy)
          relatedWorks.precededBy.map { precededBy =>
            precededBy.map {
              case RelatedWork(work, related) =>
                DisplayWork(work, includes, related)
            }
          } else None,
      succeededBy =
        if (includes.succeededBy)
          relatedWorks.succeededBy.map { succeededBy =>
            succeededBy.map {
              case RelatedWork(work, related) =>
                DisplayWork(work, includes, related)
            }
          } else None,
    )
}
