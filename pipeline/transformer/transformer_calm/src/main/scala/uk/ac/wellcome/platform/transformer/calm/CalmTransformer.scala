package uk.ac.wellcome.platform.transformer.calm

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.InvisibilityReason._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.platform.transformer.calm.models.CalmTransformerException
import uk.ac.wellcome.platform.transformer.calm.models.CalmTransformerException._

object CalmTransformer
    extends Transformer[CalmRecord]
    with CalmOps
    with Logging {

  val identifierMapping = Map(
    "RefNo" -> CalmIdentifierTypes.refNo,
    "AltRefNo" -> CalmIdentifierTypes.altRefNo,
    "BNumber" -> IdentifierType("sierra-system-number")
  )

  val notesMapping = List(
    ("AdminHistory", BiographicalNote(_)),
    ("CustodHistory", OwnershipNote(_)),
    ("Acquisition", AcquisitionNote(_)),
    ("Appraisal", AppraisalNote(_)),
    ("Accruals", AccrualsNote(_)),
    ("RelatedMaterial", RelatedMaterial(_)),
    ("PubInNote", PublicationsNote(_)),
    ("UserWrapped4", FindingAids(_)),
    ("Copyright", CopyrightNote(_)),
    ("ReproductionConditions", TermsOfUse(_)),
    ("Arrangement", ArrangementNote(_))
  )

  def apply(record: CalmRecord, version: Int): Result[TransformedBaseWork] =
    if (shouldSuppress(record)) {
      Right(
        UnidentifiedInvisibleWork(
          sourceIdentifier = sourceIdentifier(record),
          version = version,
          data = workData(record).getOrElse(WorkData()),
          invisibilityReasons = List(SuppressedFromSource("Calm"))
        )
      )
    } else {
      workData(record) match {
        case Right(data) =>
          Right(
            UnidentifiedWork(
              sourceIdentifier = sourceIdentifier(record),
              version = version,
              data = data
            ))

        case Left(err) =>
          err match {
            case knownErr: CalmTransformerException =>
              Right(
                UnidentifiedInvisibleWork(
                  sourceIdentifier = sourceIdentifier(record),
                  version = version,
                  data = WorkData(),
                  invisibilityReasons =
                    List(knownErrToUntransformableReason(knownErr))))
            case unknownStatus: UnknownAccessStatus =>
              Right(
                UnidentifiedInvisibleWork(
                  sourceIdentifier = sourceIdentifier(record),
                  version = version,
                  data = WorkData(),
                  invisibilityReasons =
                    List(InvalidValueInSourceField("Calm:AccessStatus"))))
            case err: Exception => Left(err)
          }
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
      .forall { value =>
        value.toLowerCase.trim match {
          // As much as it might not look like it, these values mean it should
          // not be suppressed.
          case "catalogued" | "not yet available" | "partially catalogued" =>
            false
          case _ => true
        }
      }

  def workData(record: CalmRecord): Result[WorkData[Unminted, Identifiable]] =
    for {
      accessStatus <- accessStatus(record)
      title <- title(record)
      collectionLevel <- collectionLevel(record)
      collectionPath <- collectionPath(record, collectionLevel)
      language <- language(record)
    } yield
      WorkData(
        title = Some(title),
        otherIdentifiers = otherIdentifiers(record),
        workType = Some(workType(collectionLevel)),
        collectionPath = Some(collectionPath),
        subjects = subjects(record),
        language = language,
        mergeCandidates = mergeCandidates(record),
        items = items(record, accessStatus),
        contributors = contributors(record),
        description = description(record),
        physicalDescription = physicalDescription(record),
        production = production(record),
        notes = notes(record)
      )

  def sourceIdentifier(record: CalmRecord): SourceIdentifier =
    SourceIdentifier(
      value = record.id,
      identifierType = CalmIdentifierTypes.recordId,
    )

  def otherIdentifiers(record: CalmRecord): List[SourceIdentifier] =
    identifierMapping.toList.flatMap {
      case (key, idType) =>
        record
          .getList(key)
          .map(id => SourceIdentifier(identifierType = idType, value = id))
    }

  def mergeCandidates(record: CalmRecord): List[MergeCandidate] =
    record
      .get("BNumber")
      .map { id =>
        MergeCandidate(
          SourceIdentifier(
            identifierType = IdentifierType("sierra-system-number"),
            ontologyType = "Work",
            value = id
          )
        )
      }
      .toList

  def title(record: CalmRecord): Result[String] =
    record
      .get("Title")
      .map(Right(_))
      .getOrElse(Left(TitleMissing))

  def workType(level: CollectionLevel): WorkType =
    level match {
      case CollectionLevel.Collection => WorkType.ArchiveCollection
      case CollectionLevel.Section    => WorkType.ArchiveSection
      case CollectionLevel.Series     => WorkType.ArchiveSeries
      case CollectionLevel.Item       => WorkType.ArchiveItem
    }

  def collectionPath(record: CalmRecord,
                     level: CollectionLevel): Result[CollectionPath] =
    record
      .get("RefNo")
      .map { path =>
        Right(
          CollectionPath(
            path = path,
            level = Some(level),
            label = record.get("AltRefNo"))
        )
      }
      .getOrElse(Left(RefNoMissing))

  def collectionLevel(record: CalmRecord): Result[CollectionLevel] =
    record
      .get("Level")
      .map(_.toLowerCase)
      .map {
        case "collection"       => Right(CollectionLevel.Collection)
        case "section"          => Right(CollectionLevel.Section)
        case "subsection"       => Right(CollectionLevel.Section)
        case "subsubsection"    => Right(CollectionLevel.Section)
        case "subsubsubsection" => Right(CollectionLevel.Section)
        case "series"           => Right(CollectionLevel.Series)
        case "subseries"        => Right(CollectionLevel.Series)
        case "subsubseries"     => Right(CollectionLevel.Series)
        case "subsubsubseries"  => Right(CollectionLevel.Series)
        case "item"             => Right(CollectionLevel.Item)
        case "piece"            => Right(CollectionLevel.Item)
        case level              => Left(UnrecognisedLevel)
      }
      .getOrElse(Left(LevelMissing))

  def items(record: CalmRecord,
            status: Option[AccessStatus]): List[Item[Unminted]] =
    List(
      Item(
        title = None,
        locations = List(physicalLocation(record, status))
      )
    )

  def physicalLocation(record: CalmRecord,
                       status: Option[AccessStatus]): PhysicalLocation =
    PhysicalLocation(
      locationType = LocationType("scmac"),
      label = "Closed stores Arch. & MSS",
      accessConditions = accessCondition(record, status).filterEmpty.toList
    )

  def accessCondition(record: CalmRecord,
                      status: Option[AccessStatus]): AccessCondition =
    AccessCondition(
      status = status,
      terms = record.getJoined("AccessConditions"),
      to = status match {
        case Some(AccessStatus.Closed)     => record.get("ClosedUntil")
        case Some(AccessStatus.Restricted) => record.get("UserDate1")
        case _                             => None
      }
    )

  def accessStatus(record: CalmRecord): Result[Option[AccessStatus]] =
    record
      .get("AccessStatus")
      .map(AccessStatus(_))
      .toResult

  def description(record: CalmRecord): Option[String] =
    record.getJoined("Description").map(NormaliseText(_))

  def physicalDescription(record: CalmRecord): Option[String] =
    (record.getList("Extent") ++ record.getList("UserWrapped6")) match {
      case Nil  => None
      case strs => Some(NormaliseText(strs.mkString(" ")))
    }

  def production(record: CalmRecord): List[ProductionEvent[Unminted]] = {
    record.getList("Date") match {
      case Nil => Nil
      case dates =>
        List(
          ProductionEvent(
            dates = dates.map(Period(_)),
            label = dates.mkString(" "),
            places = Nil,
            agents = Nil,
            function = None))
    }
  }

  def subjects(record: CalmRecord): List[Subject[Unminted]] =
    record.getList("Subject").map(Subject(_, Nil))

  def language(record: CalmRecord): Result[Option[Language]] =
    record
      .get("Language")
      .map(Language.fromLabel)
      .toResult

  def contributors(record: CalmRecord): List[Contributor[Unminted]] =
    record.getList("CreatorName").map { name =>
      Contributor(Agent(name), Nil)
    }

  def notes(record: CalmRecord): List[Note] =
    notesMapping.flatMap {
      case (key, createNote) =>
        record
          .getList(key)
          .map(NormaliseText(_))
          .map(createNote)
    }
}
