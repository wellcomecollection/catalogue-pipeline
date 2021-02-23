package uk.ac.wellcome.platform.transformer.calm

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.InvisibilityReason._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.platform.transformer.calm.models.{
  CalmSourceData,
  CalmTransformerException
}
import uk.ac.wellcome.platform.transformer.calm.models.CalmTransformerException._
import uk.ac.wellcome.models.work.internal.DeletedReason.{
  DeletedFromSource,
  SuppressedFromSource
}
import uk.ac.wellcome.models.work.internal.IdState.Identifiable
import uk.ac.wellcome.platform.transformer.calm.periods.PeriodParser
import uk.ac.wellcome.platform.transformer.calm.transformers.{
  CalmItems,
  CalmLanguages,
  CalmNotes
}
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.transformer.Transformer
import WorkState.Source

object CalmTransformer
    extends Transformer[CalmSourceData]
    with CalmRecordOps
    with Logging {

  val identifierMapping = Map(
    "RefNo" -> CalmIdentifierTypes.refNo,
    "AltRefNo" -> CalmIdentifierTypes.altRefNo,
    "BNumber" -> IdentifierType("sierra-system-number")
  )

  // As much as it might not look like it, these values mean it should
  // not be suppressed.
  val nonSuppressedStatuses = List(
    "catalogued",
    "not yet available",
    "partially catalogued",
    "third-party metadata"
  )

  override def apply(sourceData: CalmSourceData,
                     version: Int): Result[Work[Source]] = sourceData match {
    case CalmSourceData(record, isDeleted) if isDeleted =>
      Right(deletedWork(version, record, DeletedFromSource("Calm")))
    case CalmSourceData(record, _) if shouldSuppress(record) =>
      Right(deletedWork(version, record, SuppressedFromSource("Calm")))
    case CalmSourceData(record, _) =>
      tryParseValidWork(record, version)
  }

  def apply(record: CalmRecord, version: Int): Result[Work[Source]] =
    CalmTransformer(CalmSourceData(record), version)

  def deletedWork(
    version: Int,
    record: CalmRecord,
    reason: DeletedReason
  ): Work.Deleted[Source] =
    Work.Deleted[Source](
      state = Source(sourceIdentifier(record), record.retrievedAt),
      data = workData(record).getOrElse(WorkData[DataState.Unidentified]()),
      version = version,
      deletedReason = reason
    )

  private def tryParseValidWork(record: CalmRecord,
                                version: Int): Either[Exception, Work[Source]] =
    workData(record) match {
      case Right(data) =>
        Right(
          Work.Visible[Source](
            state = Source(sourceIdentifier(record), record.retrievedAt),
            version = version,
            data = data
          ))

      case Left(err) =>
        err match {
          case knownErr: CalmTransformerException =>
            Right(
              Work.Invisible[Source](
                state = Source(sourceIdentifier(record), record.retrievedAt),
                version = version,
                data = WorkData(),
                invisibilityReasons =
                  List(knownErrToUntransformableReason(knownErr))
              ))
          case unknownStatus: UnknownAccessStatus =>
            warn(
              s"${record.id}: unknown access status: ${unknownStatus.getMessage}")
            Right(
              Work.Invisible[Source](
                state = Source(sourceIdentifier(record), record.retrievedAt),
                version = version,
                data = WorkData(),
                invisibilityReasons =
                  List(InvalidValueInSourceField("Calm:AccessStatus"))
              ))
          case err: Exception => Left(err)
        }
    }

  private def knownErrToUntransformableReason(
    err: CalmTransformerException): InvisibilityReason =
    err match {
      case TitleMissing      => SourceFieldMissing("Calm:Title")
      case RefNoMissing      => SourceFieldMissing("Calm:RefNo")
      case LevelMissing      => SourceFieldMissing("Calm:Level")
      case UnrecognisedLevel => InvalidValueInSourceField("Calm:Level")
    }

  def shouldSuppress(record: CalmRecord): Boolean =
    catalogueStatusSuppressesRecord(record) match {
      case true  => true
      case false =>
        // Records prefixed with AMSG (Archives and Manuscripts Resource Guides)
        // are not actual archives but instead guides for researchers, so we
        // suppress them here.
        record
          .get("RefNo")
          .exists(_.startsWith("AMSG"))
    }

  // We suppress unless CatalogueStatus exists and is valid
  def catalogueStatusSuppressesRecord(record: CalmRecord): Boolean =
    record
      .get("CatalogueStatus")
      .forall { status =>
        !nonSuppressedStatuses.contains(status.toLowerCase.trim)
      }

  def workData(record: CalmRecord): Result[WorkData[DataState.Unidentified]] = {
    val (languages, languageNote) = CalmLanguages(
      languageField = record.get("Language")
    )

    for {
      items <- CalmItems(record)
      title <- title(record)
      workType <- workType(record)
      collectionPath <- collectionPath(record)
    } yield
      WorkData[DataState.Unidentified](
        title = Some(title),
        otherIdentifiers = otherIdentifiers(record),
        format = Some(Format.ArchivesAndManuscripts),
        collectionPath = Some(collectionPath),
        subjects = subjects(record),
        languages = languages,
        mergeCandidates = mergeCandidates(record),
        items = items,
        contributors = contributors(record),
        description = description(record),
        physicalDescription = physicalDescription(record),
        production = production(record),
        workType = workType,
        notes = CalmNotes(record, languageNote = languageNote),
      )
  }

  def sourceIdentifier(record: CalmRecord): SourceIdentifier =
    SourceIdentifier(
      value = record.id,
      identifierType = CalmIdentifierTypes.recordId,
      ontologyType = "Work"
    )

  def otherIdentifiers(record: CalmRecord): List[SourceIdentifier] =
    identifierMapping.toList.flatMap {
      case (key, idType) =>
        record
          .getList(key)
          .map(
            id =>
              SourceIdentifier(
                identifierType = idType,
                value = id,
                ontologyType = "Work"
            )
          )
    }

  def mergeCandidates(record: CalmRecord): List[MergeCandidate[Identifiable]] =
    record
      .get("BNumber")
      .map { id =>
        MergeCandidate(
          Identifiable(
            SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = id
            )
          )
        )
      }
      .toList

  def title(record: CalmRecord): Result[String] =
    record
      .get("Title")
      .map(NormaliseText(_, whitelist = NormaliseText.onlyItalics))
      .map(Right(_))
      .getOrElse(Left(TitleMissing))

  def collectionPath(record: CalmRecord): Result[CollectionPath] =
    record
      .get("RefNo")
      .map { path =>
        Right(
          CollectionPath(path = path, label = record.get("AltRefNo"))
        )
      }
      .getOrElse(Left(RefNoMissing))

  def workType(record: CalmRecord): Result[WorkType] =
    record
      .get("Level")
      .map(_.toLowerCase)
      .map {
        case "collection"       => Right(WorkType.Collection)
        case "section"          => Right(WorkType.Section)
        case "subsection"       => Right(WorkType.Section)
        case "subsubsection"    => Right(WorkType.Section)
        case "subsubsubsection" => Right(WorkType.Section)
        case "series"           => Right(WorkType.Series)
        case "subseries"        => Right(WorkType.Series)
        case "subsubseries"     => Right(WorkType.Series)
        case "subsubsubseries"  => Right(WorkType.Series)
        case "item"             => Right(WorkType.Standard)
        case "piece"            => Right(WorkType.Standard)
        case level =>
          warn(s"${record.id} has an unrecognised level: $level")
          Left(UnrecognisedLevel)
      }
      .getOrElse(Left(LevelMissing))

  def description(record: CalmRecord): Option[String] =
    record.getJoined("Description").map(NormaliseText(_))

  def physicalDescription(record: CalmRecord): Option[String] =
    (record.getList("Extent") ++ record.getList("UserWrapped6")) match {
      case Nil  => None
      case strs => Some(NormaliseText(strs.mkString(" ")))
    }

  def production(
    record: CalmRecord): List[ProductionEvent[IdState.Unminted]] = {
    record.getList("Date") match {
      case Nil => Nil
      case dates =>
        List(
          ProductionEvent(
            dates = dates.map(date => Period(date, PeriodParser(date))),
            label = dates.mkString(" "),
            places = Nil,
            agents = Nil,
            function = None))
    }
  }

  def subjects(record: CalmRecord): List[Subject[IdState.Unminted]] =
    record
      .getList("Subject")
      .map { label =>
        val normalisedLabel =
          NormaliseText(label, whitelist = NormaliseText.none)
        Subject(
          label = normalisedLabel,
          concepts = List(Concept(normalisedLabel))
        )
      }

  def contributors(record: CalmRecord): List[Contributor[IdState.Unminted]] =
    record.getList("CreatorName").map { name =>
      Contributor(Agent(name), Nil)
    }
}
