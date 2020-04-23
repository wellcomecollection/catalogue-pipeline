package uk.ac.wellcome.platform.transformer.calm

import grizzled.slf4j.Logging

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._

object CalmTransformer extends Transformer[CalmRecord] with CalmOps with Logging {

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
    if (shouldSuppress(record))
      Right(
        UnidentifiedInvisibleWork(
          sourceIdentifier = sourceIdentifier(record),
          version = version,
          data = workData(record).getOrElse(WorkData())
        )
      )
    else
      workData(record) map { data =>
        UnidentifiedWork(
          sourceIdentifier = sourceIdentifier(record),
          version = version,
          data = data
        )
      }

  def shouldSuppress(record: CalmRecord): Boolean =
    record
      .get("Transmission")
      .map { value =>
        value.toLowerCase match {
          case "no"  => true
          case "yes" => false
          case _ =>
            info(
              s"Unrecognised value for Transmission field; assuming 'Yes': $value")
            false
        }
      }
      .getOrElse(false)

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
        physicalDescription = physicalDescription(record),
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
      .getOrElse(Left(new Exception("Title field not found")))

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
      .getOrElse(Left(new Exception("RefNo field not found")))

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
        case level              => Left(new Exception(s"Unrecognised level: $level"))
      }
      .getOrElse(Left(new Exception("Level field not found.")))

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

  def physicalDescription(record: CalmRecord): Option[String] =
    (record.getList("Extent") ++ record.getList("UserWrapped6")) match {
      case Nil  => None
      case strs => Some(strs.mkString(" "))
    }

  def subjects(record: CalmRecord): List[Subject[Unminted]] =
    record.getList("Subject").map(Subject(_, Nil))

  def language(record: CalmRecord): Result[Option[Language]] =
    record
      .get("Language")
      .map(Language.fromLabel(_))
      .toResult

  def contributors(record: CalmRecord): List[Contributor[Unminted]] =
    record.getList("CreatorName").map { name =>
      Contributor(Agent(name), Nil)
    }

  def notes(record: CalmRecord): List[Note] =
    notesMapping.flatMap {
      case (key, createNote) => record.getList(key).map(createNote)
    }
}
