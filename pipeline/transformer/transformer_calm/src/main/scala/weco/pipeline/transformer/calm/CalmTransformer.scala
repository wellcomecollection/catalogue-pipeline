package weco.pipeline.transformer.calm

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.work.DeletedReason.{
  DeletedFromSource,
  SuppressedFromSource
}
import weco.catalogue.internal_model.work.InvisibilityReason._
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.calm.models.CalmTransformerException._
import weco.pipeline.transformer.calm.models.{
  CalmRecordOps,
  CalmSourceData,
  CalmTransformerException
}
import weco.pipeline.transformer.calm.transformers._
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.transformers.ParsedPeriod

object CalmTransformer
    extends Transformer[CalmSourceData]
    with CalmRecordOps
    with Logging {

  val identifierMapping = Map(
    "RefNo" -> IdentifierType.CalmRefNo,
    "AltRefNo" -> IdentifierType.CalmAltRefNo,
    "BNumber" -> IdentifierType.SierraSystemNumber,
    "AccNo" -> IdentifierType.AccessionNumber
  )

  // As much as it might not look like it, these values mean it should
  // not be suppressed.
  val nonSuppressedStatuses = List(
    "catalogued",
    "not yet available",
    "partially catalogued",
    "third-party metadata"
  )

  override def apply(
    id: String,
    sourceData: CalmSourceData,
    version: Int
  ): Result[Work[Source]] = sourceData match {
    case CalmSourceData(record, isDeleted) if isDeleted =>
      Right(deletedWork(version, record, DeletedFromSource("Calm")))
    case CalmSourceData(record, _) if shouldSuppress(record) =>
      Right(deletedWork(version, record, SuppressedFromSource("Calm")))
    case CalmSourceData(record, _) =>
      tryParseValidWork(record, version)
  }

  def apply(record: CalmRecord, version: Int): Result[Work[Source]] =
    CalmTransformer(record.id, CalmSourceData(record), version)

  def deletedWork(
    version: Int,
    record: CalmRecord,
    reason: DeletedReason
  ): Work.Deleted[Source] =
    Work.Deleted[Source](
      state = Source(
        sourceIdentifier = sourceIdentifier(record),
        sourceModifiedTime = record.retrievedAt
      ),
      version = version,
      deletedReason = reason
    )

  private def tryParseValidWork(
    record: CalmRecord,
    version: Int
  ): Either[Exception, Work[Source]] =
    workData(record) match {
      case Right(data) =>
        Right(
          Work.Visible[Source](
            state = Source(
              sourceIdentifier = sourceIdentifier(record),
              sourceModifiedTime = record.retrievedAt,
              mergeCandidates = CalmMergeCandidates(record)
            ),
            version = version,
            data = data
          )
        )

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
              )
            )
          case err: Exception => Left(err)
        }
    }

  private def knownErrToUntransformableReason(
    err: CalmTransformerException
  ): InvisibilityReason =
    err match {
      case TitleMissing => SourceFieldMissing("Calm:Title")
      case RefNoMissing => SourceFieldMissing("Calm:RefNo")
      case LevelMissing => SourceFieldMissing("Calm:Level")
      case SuppressedLevel(level) =>
        UnableToTransform(s"Calm:Suppressed level - $level")
      case UnrecognisedLevel(level) =>
        InvalidValueInSourceField(s"Calm:Level - $level")
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
      .forall {
        status =>
          !nonSuppressedStatuses.contains(status.toLowerCase.trim)
      }

  def workData(record: CalmRecord): Result[WorkData[DataState.Unidentified]] = {
    val (languages, languageNotes) = CalmLanguages(record.getList("Language"))

    for {
      title <- title(record)
      workType <- workType(record)
      collectionPath <- collectionPath(record)
    } yield WorkData[DataState.Unidentified](
      title = Some(title),
      alternativeTitles = CalmAlternativeTitles(record),
      otherIdentifiers = otherIdentifiers(record),
      format = Some(Format.ArchivesAndManuscripts),
      collectionPath = Some(collectionPath),
      referenceNumber = collectionPath.label.map(ReferenceNumber(_)),
      subjects = subjects(record),
      languages = languages,
      items = CalmItems(record),
      contributors = CalmContributors(record),
      description = description(record),
      physicalDescription = physicalDescription(record),
      production = production(record),
      workType = workType,
      notes = CalmNotes(record) ++ languageNotes ++ CalmTermsOfUse(record)
    )
  }

  def sourceIdentifier(record: CalmRecord): SourceIdentifier =
    SourceIdentifier(
      value = record.id,
      identifierType = IdentifierType.CalmRecordIdentifier,
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
                value = NormaliseText(id, whitelist = NormaliseText.none),
                ontologyType = "Work"
              )
          )
    }

  def title(record: CalmRecord): Result[String] =
    record
      .get("Title")
      .map(NormaliseText(_, whitelist = NormaliseText.onlyItalics))
      .map(Right(_))
      .getOrElse(Left(TitleMissing))

  def collectionPath(record: CalmRecord): Result[CollectionPath] =
    record
      .get("RefNo")
      .map {
        path =>
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
        // We choose not to support Group of Pieces records as these are being
        // removed from the source data.
        // See conversation here for more context:
        // https://wellcome.slack.com/archives/C8X9YKM5X/p1615367956007300
        case level @ "group of pieces" => Left(SuppressedLevel(level))
        case level =>
          warn(s"${record.id} has an unrecognised level: $level")
          Left(UnrecognisedLevel(level))
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
    record: CalmRecord
  ): List[ProductionEvent[IdState.Unminted]] = {
    record.getList("Date") match {
      case Nil => Nil
      case dates =>
        List(
          ProductionEvent(
            dates = dates.map(ParsedPeriod(_)),
            label = dates.mkString(" "),
            places = Nil,
            agents = Nil,
            function = None
          )
        )
    }
  }

  def subjects(record: CalmRecord): List[Subject[IdState.Unminted]] =
    record
      .getList("Subject")
      .map {
        label =>
          val normalisedLabel =
            NormaliseText(label, whitelist = NormaliseText.none)
          Subject(
            label = normalisedLabel,
            concepts = List(Concept(normalisedLabel))
          )
      }
}
