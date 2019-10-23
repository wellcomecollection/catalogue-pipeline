package uk.ac.wellcome.models.work.internal

import scala.language.higherKinds

sealed trait BaseWork {
  val version: Int
  val sourceIdentifier: SourceIdentifier
}

sealed trait IdentifiedBaseWork extends BaseWork {
  val canonicalId: String
}

sealed trait TransformedBaseWork extends BaseWork

sealed trait InvisibleWork extends BaseWork

sealed trait RedirectedWork extends BaseWork {
  val redirect: Redirect
}

case class WorkData[+IdState[+S] <: IdentityState[S]](
  title: String,
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
  data: WorkData[MaybeDisplayable],
  ontologyType: String = "Work",
  identifiedType: String = classOf[IdentifiedWork].getSimpleName
) extends TransformedBaseWork with MultipleSourceIdentifiers {
  val otherIdentifiers = data.otherIdentifiers
}

case class IdentifiedWork(
  canonicalId: String,
  version: Int,
  sourceIdentifier: SourceIdentifier,
  data: WorkData[Displayable],
  ontologyType: String = "Work"
) extends IdentifiedBaseWork with MultipleSourceIdentifiers {
  val otherIdentifiers = data.otherIdentifiers

  def withData(f: WorkData[Displayable] => WorkData[Displayable]) =
    this.copy(data = f(data))
}

case class UnidentifiedInvisibleWork(
  version: Int,
  sourceIdentifier: SourceIdentifier,
  identifiedType: String = classOf[IdentifiedInvisibleWork].getSimpleName
) extends TransformedBaseWork with InvisibleWork

case class IdentifiedInvisibleWork(
  canonicalId: String,
  version: Int,
  sourceIdentifier: SourceIdentifier,
) extends IdentifiedBaseWork with InvisibleWork

case class UnidentifiedRedirectedWork(
  sourceIdentifier: SourceIdentifier,
  version: Int,
  redirect: IdentifiableRedirect,
  identifiedType: String = classOf[IdentifiedRedirectedWork].getSimpleName
) extends TransformedBaseWork with RedirectedWork

case class IdentifiedRedirectedWork(
  canonicalId: String,
  sourceIdentifier: SourceIdentifier,
  version: Int,
  redirect: IdentifiedRedirect
) extends IdentifiedBaseWork with RedirectedWork
