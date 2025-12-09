package weco.catalogue.internal_model

import io.circe.generic.extras.semiauto._
import io.circe._
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image._
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  DigitalLocation,
  DigitalLocationType,
  License,
  Location,
  LocationType,
  PhysicalLocation,
  PhysicalLocationType
}
import weco.catalogue.internal_model.work._
import weco.json.JsonUtil._

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).
  //
  // The particular implicits defined here have been chosen by generating
  // flamegraphs using the scalac-profiling plugin. See this blog post for
  // info: https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html
  //
  // NOTE: the ordering here is important: we derive ImageData[_]
  // then WorkData, then the other images and works due to the order of
  // dependencies (thus preventing duplicate work)

  implicit val _decAccessCondition: Decoder[AccessCondition] =
    deriveConfiguredDecoder
  implicit val _decNote: Decoder[Note] = deriveConfiguredDecoder

  implicit val _decIdentifierType: Decoder[IdentifierType] =
    IdentifierType.identifierTypeDecoder
  implicit val _decSourceIdentifier: Decoder[SourceIdentifier] =
    deriveConfiguredDecoder
  implicit val _decCanonicalId: Decoder[CanonicalId] = CanonicalId.decoder
  implicit val _decReferenceNumber: Decoder[ReferenceNumber] =
    ReferenceNumber.decoder

  implicit val _decIdStateIdentified: Decoder[IdState.Identified] =
    deriveConfiguredDecoder
  implicit val _decIdStateIdentifiable: Decoder[IdState.Identifiable] =
    deriveConfiguredDecoder
  implicit val _decIdStateUnminted: Decoder[IdState.Unminted] =
    deriveConfiguredDecoder
  implicit val _decIdStateMinted: Decoder[IdState.Minted] =
    deriveConfiguredDecoder

  implicit val _decLanguage: Decoder[Language] = deriveConfiguredDecoder

  implicit val _decLicense: Decoder[License] = License.licenseDecoder
  implicit val _decPhysicalLocationType: Decoder[PhysicalLocationType] =
    LocationType.physicalLocationTypeDecoder
  implicit val _decDigitalLocationType: Decoder[DigitalLocationType] =
    LocationType.digitalLocationTypeDecoder
  implicit val _decLocationType: Decoder[LocationType] =
    LocationType.locationTypeDecoder
  implicit val _decDigitalLocation: Decoder[DigitalLocation] =
    deriveConfiguredDecoder
  implicit val _decPhysicalLocation: Decoder[PhysicalLocation] =
    deriveConfiguredDecoder
  implicit val _decLocation: Decoder[Location] = deriveConfiguredDecoder

  implicit val _decMergeCandidateIdentifiable
    : Decoder[MergeCandidate[IdState.Identifiable]] =
    deriveConfiguredDecoder
  implicit val _decMergeCandidateIdentified
    : Decoder[MergeCandidate[IdState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decAgentUnminted: Decoder[Agent[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decAgentMinted: Decoder[Agent[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _decMeetingUnminted: Decoder[Meeting[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decMeetingMinted: Decoder[Meeting[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _decOrganisationUnminted
    : Decoder[Organisation[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decOrganisationMinted: Decoder[Organisation[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _decPersonUnminted: Decoder[Person[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decPersonMinted: Decoder[Person[IdState.Minted]] =
    deriveConfiguredDecoder

  // The four classes above (Agent, Meeting, Organisation, Person) are the
  // implementations of AbstractAgent.  We deliberately wait until we have those
  // decoders before creating these two decoders.
  implicit val _decAbstractAgentUnminted
    : Decoder[AbstractAgent[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decAbstractAgentMinted: Decoder[AbstractAgent[IdState.Minted]] =
    deriveConfiguredDecoder

  implicit val _decConceptUnminted: Decoder[Concept[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decConceptMinted: Decoder[Concept[IdState.Minted]] =
    deriveConfiguredDecoder

  implicit val _decInstantRange: Decoder[InstantRange] = deriveConfiguredDecoder
  implicit val _decPeriodUnminted: Decoder[Period[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decPeriodMinted: Decoder[Period[IdState.Minted]] =
    deriveConfiguredDecoder

  implicit val _decPlaceUnminted: Decoder[Place[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decPlaceMinted: Decoder[Place[IdState.Minted]] =
    deriveConfiguredDecoder

  // The classes above (Concept, Period, Place) are the implementations of
  // AbstractConcept.  We deliberately wait until we have those decoders
  // before creating these two decoders.
  implicit val _decAbstractConceptUnminted
    : Decoder[AbstractConcept[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decAbstractConceptMinted
    : Decoder[AbstractConcept[IdState.Minted]] =
    deriveConfiguredDecoder

  // A ProductionEvent uses AbstractAgent, Concept, Period and Place.
  implicit val _decProductionEventUnminted
    : Decoder[ProductionEvent[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decProductionEventMinted
    : Decoder[ProductionEvent[IdState.Minted]] =
    deriveConfiguredDecoder

  // An AbstractRootConcept is one of AbstractAgent and AbstractConcept
  implicit val _decAbstractRootConceptUnminted
    : Decoder[AbstractRootConcept[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decAbstractRootConceptMinted
    : Decoder[AbstractRootConcept[IdState.Minted]] =
    deriveConfiguredDecoder

  // Contributor, Genre, and Subject all use a mixture of AbstractAgent,
  // AbstractConcept and AbstractRootConcept.
  implicit val _decContributorUnminted: Decoder[Contributor[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decContributorMinted: Decoder[Contributor[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _decGenreUnminted: Decoder[Genre[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decGenreMinted: Decoder[Genre[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _decSubjectUnminted: Decoder[Subject[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decSubjectMinted: Decoder[Subject[IdState.Minted]] =
    deriveConfiguredDecoder

  implicit val _decItemUnminted: Decoder[Item[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _decItemMinted: Decoder[Item[IdState.Minted]] =
    deriveConfiguredDecoder

  implicit val _decFormat: Decoder[Format] = Format.formatDecoder
  implicit val _decHoldings: Decoder[Holdings] = deriveConfiguredDecoder
  implicit val _decWorkType: Decoder[WorkType] = WorkType.workTypeDecoder
  implicit val _decCollectionPath: Decoder[CollectionPath] =
    deriveConfiguredDecoder
  implicit val _decRelation: Decoder[Relation] = deriveConfiguredDecoder
  implicit val _decRelations: Decoder[Relations] = deriveConfiguredDecoder

  implicit val _decInferredData: Decoder[InferredData] =
    deriveConfiguredDecoder

  implicit val _decImageDataIdentifiable
    : Decoder[ImageData[IdState.Identifiable]] =
    deriveConfiguredDecoder
  implicit val _decImageDataIdentified: Decoder[ImageData[IdState.Identified]] =
    deriveConfiguredDecoder

  implicit val _dec45: Decoder[WorkData[DataState.Unidentified]] =
    deriveConfiguredDecoder
  implicit val _dec46: Decoder[WorkData[DataState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decInvisibilityReason: Decoder[InvisibilityReason] =
    deriveConfiguredDecoder
  implicit val _decDeletedReason: Decoder[DeletedReason] =
    deriveConfiguredDecoder

  implicit val _decAvailability: Decoder[Availability] =
    Availability.availabilityDecoder

  implicit val _decInternalWorkSource: Decoder[InternalWork.Source] =
    deriveConfiguredDecoder
  implicit val _decInternalWorkIdentified: Decoder[InternalWork.Identified] =
    deriveConfiguredDecoder

  implicit val _decWorkStateSource: Decoder[WorkState.Source] =
    deriveConfiguredDecoder
  implicit val _decWorkStateMerged: Decoder[WorkState.Merged] =
    deriveConfiguredDecoder
  implicit val _decWorkStateIdentified: Decoder[WorkState.Identified] =
    deriveConfiguredDecoder

  implicit val _decWorkVisibleSource: Decoder[Work.Visible[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkVisibleMerged: Decoder[Work.Visible[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkVisibleIdentified
    : Decoder[Work.Visible[WorkState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decWorkInvisibleSource
    : Decoder[Work.Invisible[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkInvisibleMerged
    : Decoder[Work.Invisible[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkInvisibleIdentified
    : Decoder[Work.Invisible[WorkState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decWorkRedirectedSource
    : Decoder[Work.Redirected[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkRedirectedMerged
    : Decoder[Work.Redirected[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkRedirectedIdentified
    : Decoder[Work.Redirected[WorkState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decWorkDeletedSource: Decoder[Work.Deleted[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkDeletedMerged: Decoder[Work.Deleted[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkDeletedIdentified
    : Decoder[Work.Deleted[WorkState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decWorkSource: Decoder[Work[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkMerged: Decoder[Work[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkIdentified: Decoder[Work[WorkState.Identified]] =
    deriveConfiguredDecoder

  implicit val _decParentWork: Decoder[ParentWork] =
    deriveConfiguredDecoder
  implicit val _decImageSource: Decoder[ImageSource] =
    deriveConfiguredDecoder
  implicit val _decImageInitial: Decoder[Image[ImageState.Initial]] =
    deriveConfiguredDecoder
  implicit val _decImageAugmented: Decoder[Image[ImageState.Augmented]] =
    deriveConfiguredDecoder

  implicit val _encAccessCondition: Encoder[AccessCondition] =
    deriveConfiguredEncoder
  implicit val _encNote: Encoder[Note] = deriveConfiguredEncoder

  implicit val _encIdentifierType: Encoder[IdentifierType] =
    IdentifierType.identifierTypeEncoder
  implicit val _encSourceIdentifier: Encoder[SourceIdentifier] =
    deriveConfiguredEncoder
  implicit val _encCanonicalId: Encoder[CanonicalId] = CanonicalId.encoder
  implicit val _encReferenceNumber: Encoder[ReferenceNumber] =
    ReferenceNumber.encoder

  implicit val _encIdStateIdentified: Encoder[IdState.Identified] =
    deriveConfiguredEncoder
  implicit val _encIdStateIdentifiable: Encoder[IdState.Identifiable] =
    deriveConfiguredEncoder
  implicit val _encIdStateUnminted: Encoder[IdState.Unminted] =
    deriveConfiguredEncoder
  implicit val _encIdStateMinted: Encoder[IdState.Minted] =
    deriveConfiguredEncoder

  implicit val _encLanguage: Encoder[Language] = deriveConfiguredEncoder

  implicit val _encLicense: Encoder[License] = License.licenseEncoder
  implicit val _encPhysicalLocationType: Encoder[PhysicalLocationType] =
    LocationType.physicalLocationTypeEncoder
  implicit val _encDigitalLocationType: Encoder[DigitalLocationType] =
    LocationType.digitalLocationTypeEncoder
  implicit val _encLocationType: Encoder[LocationType] =
    LocationType.locationTypeEncoder
  implicit val _encDigitalLocation: Encoder[DigitalLocation] =
    deriveConfiguredEncoder
  implicit val _encPhysicalLocation: Encoder[PhysicalLocation] =
    deriveConfiguredEncoder
  implicit val _encLocation: Encoder[Location] = deriveConfiguredEncoder

  implicit val _encMergeCandidateIdentifiable
    : Encoder[MergeCandidate[IdState.Identifiable]] =
    deriveConfiguredEncoder
  implicit val _encMergeCandidateIdentified
    : Encoder[MergeCandidate[IdState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encAgentUnminted: Encoder[Agent[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encAgentMinted: Encoder[Agent[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _encMeetingUnminted: Encoder[Meeting[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encMeetingMinted: Encoder[Meeting[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _encOrganisationUnminted
    : Encoder[Organisation[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encOrganisationMinted: Encoder[Organisation[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _encPersonUnminted: Encoder[Person[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encPersonMinted: Encoder[Person[IdState.Minted]] =
    deriveConfiguredEncoder

  // The four classes above (Agent, Meeting, Organisation, Person) are the
  // implementations of AbstractAgent.  We deliberately wait until we have those
  // Encoders before creating these two Encoders.
  implicit val _encAbstractAgentUnminted
    : Encoder[AbstractAgent[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encAbstractAgentMinted: Encoder[AbstractAgent[IdState.Minted]] =
    deriveConfiguredEncoder

  implicit val _encConceptUnminted: Encoder[Concept[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encConceptMinted: Encoder[Concept[IdState.Minted]] =
    deriveConfiguredEncoder

  implicit val _encInstantRange: Encoder[InstantRange] = deriveConfiguredEncoder
  implicit val _encPeriodUnminted: Encoder[Period[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encPeriodMinted: Encoder[Period[IdState.Minted]] =
    deriveConfiguredEncoder

  implicit val _encPlaceUnminted: Encoder[Place[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encPlaceMinted: Encoder[Place[IdState.Minted]] =
    deriveConfiguredEncoder

  // The classes above (Concept, Period, Place) are the implementations of
  // AbstractConcept.  We deliberately wait until we have those Encoders
  // before creating these two Encoders.
  implicit val _encAbstractConceptUnminted
    : Encoder[AbstractConcept[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encAbstractConceptMinted
    : Encoder[AbstractConcept[IdState.Minted]] =
    deriveConfiguredEncoder

  // A ProductionEvent uses AbstractAgent, Concept, Period and Place.
  implicit val _encProductionEventUnminted
    : Encoder[ProductionEvent[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encProductionEventMinted
    : Encoder[ProductionEvent[IdState.Minted]] =
    deriveConfiguredEncoder

  // An AbstractRootConcept is one of AbstractAgent and AbstractConcept
  implicit val _encAbstractRootConceptUnminted
    : Encoder[AbstractRootConcept[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encAbstractRootConceptMinted
    : Encoder[AbstractRootConcept[IdState.Minted]] =
    deriveConfiguredEncoder

  // Contributor, Genre, and Subject all use a mixture of AbstractAgent,
  // AbstractConcept and AbstractRootConcept.
  implicit val _encContributorUnminted: Encoder[Contributor[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encContributorMinted: Encoder[Contributor[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _encGenreUnminted: Encoder[Genre[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encGenreMinted: Encoder[Genre[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _encSubjectUnminted: Encoder[Subject[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encSubjectMinted: Encoder[Subject[IdState.Minted]] =
    deriveConfiguredEncoder

  implicit val _encItemUnminted: Encoder[Item[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _encItemMinted: Encoder[Item[IdState.Minted]] =
    deriveConfiguredEncoder

  implicit val _encFormat: Encoder[Format] = Format.formatEncoder
  implicit val _encHoldings: Encoder[Holdings] = deriveConfiguredEncoder
  implicit val _encWorkType: Encoder[WorkType] = WorkType.workTypeEncoder
  implicit val _encCollectionPath: Encoder[CollectionPath] =
    deriveConfiguredEncoder
  implicit val _encRelation: Encoder[Relation] = deriveConfiguredEncoder
  implicit val _encRelations: Encoder[Relations] = deriveConfiguredEncoder

  implicit val _encInferredData: Encoder[InferredData] =
    deriveConfiguredEncoder

  implicit val _encImageDataIdentifiable
    : Encoder[ImageData[IdState.Identifiable]] =
    deriveConfiguredEncoder
  implicit val _encImageDataIdentified: Encoder[ImageData[IdState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encWorkDataUnidentified
    : Encoder[WorkData[DataState.Unidentified]] =
    deriveConfiguredEncoder
  implicit val _encWorkDataIdentified: Encoder[WorkData[DataState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encInvisibilityReason: Encoder[InvisibilityReason] =
    deriveConfiguredEncoder
  implicit val _encDeletedReason: Encoder[DeletedReason] =
    deriveConfiguredEncoder

  implicit val _encAvailability: Encoder[Availability] =
    Availability.availabilityEncoder

  implicit val _encInternalWorkSource: Encoder[InternalWork.Source] =
    deriveConfiguredEncoder
  implicit val _encInternalWorkIdentified: Encoder[InternalWork.Identified] =
    deriveConfiguredEncoder

  implicit val _encWorkStateSource: Encoder[WorkState.Source] =
    deriveConfiguredEncoder
  implicit val _encWorkStateMerged: Encoder[WorkState.Merged] =
    deriveConfiguredEncoder
  implicit val _encWorkStateIdentified: Encoder[WorkState.Identified] =
    deriveConfiguredEncoder

  implicit val _encWorkVisibleSource: Encoder[Work.Visible[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkVisibleMerged: Encoder[Work.Visible[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkVisibleIdentified
    : Encoder[Work.Visible[WorkState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encWorkInvisibleSource
    : Encoder[Work.Invisible[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkInvisibleMerged
    : Encoder[Work.Invisible[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkInvisibleIdentified
    : Encoder[Work.Invisible[WorkState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encWorkRedirectedSource
    : Encoder[Work.Redirected[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkRedirectedMerged
    : Encoder[Work.Redirected[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkRedirectedIdentified
    : Encoder[Work.Redirected[WorkState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encWorkDeletedSource: Encoder[Work.Deleted[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkDeletedMerged: Encoder[Work.Deleted[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkDeletedIdentified
    : Encoder[Work.Deleted[WorkState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encWorkSource: Encoder[Work[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkMerged: Encoder[Work[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkIdentified: Encoder[Work[WorkState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encParentWork: Encoder[ParentWork] =
    deriveConfiguredEncoder
  implicit val _encImageSource: Encoder[ImageSource] =
    deriveConfiguredEncoder
  implicit val _encImageInitial: Encoder[Image[ImageState.Initial]] =
    deriveConfiguredEncoder
  implicit val _encImageAugmented: Encoder[Image[ImageState.Augmented]] =
    deriveConfiguredEncoder
}
