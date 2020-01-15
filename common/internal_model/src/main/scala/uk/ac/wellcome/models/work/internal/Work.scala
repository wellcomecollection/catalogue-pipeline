package uk.ac.wellcome.models.work.internal

import scala.language.higherKinds

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
  val data: WorkData[Unminted]
  val otherIdentifiers = data.otherIdentifiers
}

sealed trait InvisibleWork extends BaseWork

sealed trait RedirectedWork extends BaseWork {
  val redirect: Redirect
}

case class WorkData[+IdState[+S] <: IdentityState[S]](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  mergeCandidates: List[MergeCandidate] = Nil,
  alternativeTitles: List[String] = Nil,
  workType: Option[WorkType] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  lettering: Option[String] = None,
  createdDate: Option[Period] = None,
  subjects: List[IdState[Subject[IdState[AbstractRootConcept]]]] = Nil,
  genres: List[Genre[IdState[AbstractConcept]]] = Nil,
  contributors: List[Contributor[IdState[AbstractAgent]]] = Nil,
  thumbnail: Option[Location] = None,
  production: List[ProductionEvent[IdState[AbstractAgent]]] = Nil,
  language: Option[Language] = None,
  edition: Option[String] = None,
  notes: List[Note] = Nil,
  duration: Option[Int] = None,
  items: List[IdState[Item]] = Nil,
  merged: Boolean = false,
)

case class UnidentifiedWork(
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Unminted],
  ontologyType: String = "Work",
  identifiedType: String = classOf[IdentifiedWork].getSimpleName
) extends TransformedBaseWork {

  def withData(f: WorkData[Unminted] => WorkData[Unminted]) =
    this.copy(data = f(data))
}

case class IdentifiedWork(
  canonicalId: String,
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Minted],
  ontologyType: String = "Work"
) extends IdentifiedBaseWork
    with MultipleSourceIdentifiers {
  val otherIdentifiers = data.otherIdentifiers

  def withData(f: WorkData[Minted] => WorkData[Minted]) =
    this.copy(data = f(data))
}

case class UnidentifiedInvisibleWork(
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Unminted],
  identifiedType: String = classOf[IdentifiedInvisibleWork].getSimpleName
) extends TransformedBaseWork
    with InvisibleWork {
  def withData(f: WorkData[Unminted] => WorkData[Unminted]) =
    this.copy(data = f(data))
}

case class IdentifiedInvisibleWork(
  canonicalId: String,
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Minted]
) extends IdentifiedBaseWork
    with InvisibleWork {
  def withData(f: WorkData[Minted] => WorkData[Minted]) =
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
