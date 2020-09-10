package uk.ac.wellcome.models.work.internal

sealed trait BaseWork {
  val version: Int
  val sourceIdentifier: SourceIdentifier
}

sealed trait IdentifiedBaseWork extends BaseWork {
  val canonicalId: String
}

sealed trait TransformedBaseWork
    extends BaseWork
    with MultipleSourceIdentifiers {
  val data: WorkData[Id.Unminted, Id.Identifiable]
  val otherIdentifiers = data.otherIdentifiers
}

object TransformedBaseWork {
  implicit class WorkToSourceWork(work: TransformedBaseWork) {
    def toSourceWork: SourceWork[Id.Identifiable, Id.Unminted] =
      SourceWork[Id.Identifiable, Id.Unminted](
        Id.Identifiable(work.sourceIdentifier),
        work.data)
  }
}

sealed trait InvisibleWork extends BaseWork

sealed trait RedirectedWork extends BaseWork {
  val redirect: Redirect
}

case class WorkData[DataId <: Id, ImageId <: Id.WithSourceIdentifier](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  mergeCandidates: List[MergeCandidate] = Nil,
  alternativeTitles: List[String] = Nil,
  workType: Option[WorkType] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  lettering: Option[String] = None,
  createdDate: Option[Period[DataId]] = None,
  subjects: List[Subject[DataId]] = Nil,
  genres: List[Genre[DataId]] = Nil,
  contributors: List[Contributor[DataId]] = Nil,
  thumbnail: Option[LocationDeprecated] = None,
  production: List[ProductionEvent[DataId]] = Nil,
  language: Option[Language] = None,
  edition: Option[String] = None,
  notes: List[Note] = Nil,
  duration: Option[Int] = None,
  items: List[Item[DataId]] = Nil,
  merged: Boolean = false,
  collectionPath: Option[CollectionPath] = None,
  images: List[UnmergedImage[ImageId, DataId]] = Nil
)

case class UnidentifiedWork(
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Id.Unminted, Id.Identifiable],
  ontologyType: String = "Work",
  identifiedType: String = classOf[IdentifiedWork].getSimpleName
) extends TransformedBaseWork {

  def withData(
    f: WorkData[Id.Unminted, Id.Identifiable] => WorkData[Id.Unminted, Id.Identifiable]) =
    this.copy(data = f(data))
}

case class IdentifiedWork(
  canonicalId: String,
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Id.Minted, Id.Identified],
  ontologyType: String = "Work"
) extends IdentifiedBaseWork
    with MultipleSourceIdentifiers {
  val otherIdentifiers = data.otherIdentifiers

  def withData(
    f: WorkData[Id.Minted, Id.Identified] => WorkData[Id.Minted, Id.Identified]) =
    this.copy(data = f(data))
}

object IdentifiedWork {
  implicit class WorkToSourceWork(work: IdentifiedWork) {
    def toSourceWork: SourceWork[Id.Identified, Id.Minted] =
      SourceWork[Id.Identified, Id.Minted](
        Id.Identified(
          work.canonicalId,
          work.sourceIdentifier,
          work.otherIdentifiers),
        work.data)
  }
}

case class UnidentifiedInvisibleWork(
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Id.Unminted, Id.Identifiable],
  invisibilityReasons: List[InvisibilityReason] = Nil,
  identifiedType: String = classOf[IdentifiedInvisibleWork].getSimpleName
) extends TransformedBaseWork
    with InvisibleWork {
  def withData(
    f: WorkData[Id.Unminted, Id.Identifiable] => WorkData[Id.Unminted, Id.Identifiable]) =
    this.copy(data = f(data))
}

case class IdentifiedInvisibleWork(
  canonicalId: String,
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Id.Minted, Id.Identified],
  invisibilityReasons: List[InvisibilityReason] = Nil,
) extends IdentifiedBaseWork
    with InvisibleWork {
  def withData(
    f: WorkData[Id.Minted, Id.Identified] => WorkData[Id.Minted, Id.Identified]) =
    this.copy(data = f(data))
}

case class UnidentifiedRedirectedWork(
  sourceIdentifier: SourceIdentifier,
  version: Int,
  redirect: IdentifiableRedirect,
  identifiedType: String = classOf[IdentifiedRedirectedWork].getSimpleName
) extends RedirectedWork

case class IdentifiedRedirectedWork(
  canonicalId: String,
  sourceIdentifier: SourceIdentifier,
  version: Int,
  redirect: IdentifiedRedirect
) extends IdentifiedBaseWork
    with RedirectedWork
