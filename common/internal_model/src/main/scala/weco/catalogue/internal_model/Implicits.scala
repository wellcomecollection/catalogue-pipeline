package weco.catalogue.internal_model

import io.circe.generic.extras.semiauto._
import io.circe._
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image._
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
import weco.catalogue.internal_model.matcher.MatcherResult
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

  implicit val _dec00: Decoder[AccessCondition] = deriveConfiguredDecoder
  implicit val _dec01: Decoder[Note] = deriveConfiguredDecoder

  implicit val _decIdentifierType: Decoder[IdentifierType] = IdentifierType.identifierTypeDecoder
  implicit val _decSourceIdentifier: Decoder[SourceIdentifier] = deriveConfiguredDecoder
  implicit val _decCanonicalId: Decoder[CanonicalId] = deriveConfiguredDecoder

  implicit val _decIdStateIdentified: Decoder[IdState.Identified] = deriveConfiguredDecoder
  implicit val _decIdStateIdentifiable: Decoder[IdState.Identifiable] = deriveConfiguredDecoder
  implicit val _decIdStateUnminted: Decoder[IdState.Unminted] = deriveConfiguredDecoder
  implicit val _decIdStateMinted: Decoder[IdState.Minted] = deriveConfiguredDecoder

  implicit val _dec05: Decoder[InstantRange] = deriveConfiguredDecoder

  implicit val _decLicense: Decoder[License] = License.licenseDecoder
  implicit val _decPhysicalLocationType: Decoder[PhysicalLocationType] = LocationType.physicalLocationTypeDecoder
  implicit val _decDigitalLocationType: Decoder[DigitalLocationType] = LocationType.digitalLocationTypeDecoder
  implicit val _decLocationType: Decoder[LocationType] = LocationType.locationTypeDecoder
  implicit val _decDigitalLocation: Decoder[DigitalLocation] =
    deriveConfiguredDecoder
  implicit val _decPhysicalLocation: Decoder[PhysicalLocation] =
    deriveConfiguredDecoder
  implicit val _decLocation: Decoder[Location] = deriveConfiguredDecoder

  implicit val _dec11: Decoder[MergeCandidate[IdState.Identifiable]] =
    deriveConfiguredDecoder
  implicit val _dec12: Decoder[MergeCandidate[IdState.Identified]] =
    deriveConfiguredDecoder
  implicit val _dec13: Decoder[MatcherResult] = deriveConfiguredDecoder
  implicit val _dec14: Decoder[Person[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec15: Decoder[Person[IdState.Minted]] = deriveConfiguredDecoder
  implicit val _dec16: Decoder[Meeting[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec17: Decoder[Meeting[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec18: Decoder[Organisation[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec19: Decoder[Organisation[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec20: Decoder[Agent[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec21: Decoder[Agent[IdState.Minted]] = deriveConfiguredDecoder
  implicit val _dec22: Decoder[AbstractAgent[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec23: Decoder[AbstractAgent[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec24: Decoder[Place[IdState.Unminted]] =
    deriveConfiguredDecoder

  implicit val _decPeriodMinted: Decoder[Period[IdState.Minted]] = deriveConfiguredDecoder
  implicit val _decPeriodUnminted: Decoder[Period[IdState.Unminted]] = deriveConfiguredDecoder

  implicit val _dec26: Decoder[Concept[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec27: Decoder[Concept[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec28: Decoder[AbstractConcept[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec29: Decoder[AbstractConcept[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec30: Decoder[AbstractRootConcept[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec31: Decoder[AbstractRootConcept[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec32: Decoder[Genre[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec33: Decoder[Genre[IdState.Minted]] = deriveConfiguredDecoder
  implicit val _dec34: Decoder[Subject[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec35: Decoder[Subject[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec36: Decoder[ProductionEvent[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec37: Decoder[ProductionEvent[IdState.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec38: Decoder[Item[IdState.Unminted]] = deriveConfiguredDecoder
  implicit val _dec39: Decoder[Item[IdState.Minted]] = deriveConfiguredDecoder
  implicit val _dec40: Decoder[Contributor[IdState.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec41: Decoder[Contributor[IdState.Minted]] =
    deriveConfiguredDecoder

  implicit val _decFormat: Decoder[Format] = Format.formatDecoder
  implicit val _decHoldings: Decoder[Holdings] = deriveConfiguredDecoder
  implicit val _decWorkType: Decoder[WorkType] = WorkType.workTypeDecoder
  implicit val _decCollectionPath: Decoder[CollectionPath] =
    deriveConfiguredDecoder
  implicit val _decRelation: Decoder[Relation] = deriveConfiguredDecoder
  implicit val _decRelations: Decoder[Relations] = deriveConfiguredDecoder

  implicit val _decDerivedWorkData: Decoder[DerivedWorkData] =
    deriveConfiguredDecoder
  implicit val _decInferredData: Decoder[InferredData] =
    deriveConfiguredDecoder
  implicit val _decDerivedImageData: Decoder[DerivedImageData] =
    deriveConfiguredDecoder

  implicit val _decImageDataIdentifiable: Decoder[ImageData[IdState.Identifiable]] =
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

  implicit val _decWorkStateSource: Decoder[WorkState.Source] =
    deriveConfiguredDecoder
  implicit val _decWorkStateMerged: Decoder[WorkState.Merged] =
    deriveConfiguredDecoder
  implicit val _decWorkStateDenormalised: Decoder[WorkState.Denormalised] =
    deriveConfiguredDecoder
  implicit val _decWorkStateIdentified: Decoder[WorkState.Identified] =
    deriveConfiguredDecoder
  implicit val _decWorkStateIndexed: Decoder[WorkState.Indexed] =
    deriveConfiguredDecoder

  implicit val _decWorkVisibleSource: Decoder[Work.Visible[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkVisibleMerged: Decoder[Work.Visible[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkVisibleDenormalised: Decoder[Work.Visible[WorkState.Denormalised]] =
    deriveConfiguredDecoder
  implicit val _decWorkVisibleIdentified: Decoder[Work.Visible[WorkState.Identified]] =
    deriveConfiguredDecoder
  implicit val _decWorkVisibleIndexed: Decoder[Work.Visible[WorkState.Indexed]] =
    deriveConfiguredDecoder

  implicit val _decWorkInvisibleSource: Decoder[Work.Invisible[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkInvisibleMerged: Decoder[Work.Invisible[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkInvisibleDenormalised: Decoder[Work.Invisible[WorkState.Denormalised]] =
    deriveConfiguredDecoder
  implicit val _decWorkInvisibleIdentified: Decoder[Work.Invisible[WorkState.Identified]] =
    deriveConfiguredDecoder
  implicit val _decWorkInvisibleIndexed: Decoder[Work.Invisible[WorkState.Indexed]] =
    deriveConfiguredDecoder

  implicit val _decWorkRedirectedSource: Decoder[Work.Redirected[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkRedirectedMerged: Decoder[Work.Redirected[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkRedirectedDenormalised: Decoder[Work.Redirected[WorkState.Denormalised]] =
    deriveConfiguredDecoder
  implicit val _decWorkRedirectedIdentified: Decoder[Work.Redirected[WorkState.Identified]] =
    deriveConfiguredDecoder
  implicit val _decWorkRedirectedIndexed: Decoder[Work.Redirected[WorkState.Indexed]] =
    deriveConfiguredDecoder

  implicit val _decWorkDeletedSource: Decoder[Work.Deleted[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkDeletedMerged: Decoder[Work.Deleted[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkDeletedDenormalised: Decoder[Work.Deleted[WorkState.Denormalised]] =
    deriveConfiguredDecoder
  implicit val _decWorkDeletedIdentified: Decoder[Work.Deleted[WorkState.Identified]] =
    deriveConfiguredDecoder
  implicit val _decWorkDeletedIndexed: Decoder[Work.Deleted[WorkState.Indexed]] =
    deriveConfiguredDecoder

  implicit val _decWorkSource: Decoder[Work[WorkState.Source]] =
    deriveConfiguredDecoder
  implicit val _decWorkMerged: Decoder[Work[WorkState.Merged]] =
    deriveConfiguredDecoder
  implicit val _decWorkDenormalised: Decoder[Work[WorkState.Denormalised]] =
    deriveConfiguredDecoder
  implicit val _decWorkIdentified: Decoder[Work[WorkState.Identified]] =
    deriveConfiguredDecoder
  implicit val _decWorkIndexed: Decoder[Work[WorkState.Indexed]] =
    deriveConfiguredDecoder

  implicit val _dec63: Decoder[ParentWorks] =
    deriveConfiguredDecoder
  implicit val _dec64: Decoder[ImageSource] =
    deriveConfiguredDecoder
  implicit val _dec65: Decoder[Image[ImageState.Initial]] =
    deriveConfiguredDecoder
  implicit val _dec66: Decoder[Image[ImageState.Augmented]] =
    deriveConfiguredDecoder
  implicit val _dec67: Decoder[Image[ImageState.Indexed]] =
    deriveConfiguredDecoder

  implicit val _enc00: Encoder[AccessCondition] = deriveConfiguredEncoder
  implicit val _enc01: Encoder[Note] = deriveConfiguredEncoder

  implicit val _encIdentifierType: Encoder[IdentifierType] = IdentifierType.identifierTypeEncoder
  implicit val _encSourceIdentifier: Encoder[SourceIdentifier] = deriveConfiguredEncoder
  implicit val _encCanonicalId: Encoder[CanonicalId] = deriveConfiguredEncoder

  implicit val _encIdStateIdentified: Encoder[IdState.Identified] = deriveConfiguredEncoder
  implicit val _encIdStateIdentifiable: Encoder[IdState.Identifiable] = deriveConfiguredEncoder
  implicit val _encIdStateUnminted: Encoder[IdState.Unminted] = deriveConfiguredEncoder
  implicit val _encIdStateMinted: Encoder[IdState.Minted] = deriveConfiguredEncoder

  implicit val _enc05: Encoder[InstantRange] = deriveConfiguredEncoder

  implicit val _encLicense: Encoder[License] = License.licenseEncoder
  implicit val _encPhysicalLocationType: Encoder[PhysicalLocationType] = LocationType.physicalLocationTypeEncoder
  implicit val _encDigitalLocationType: Encoder[DigitalLocationType] = LocationType.digitalLocationTypeEncoder
  implicit val _encLocationType: Encoder[LocationType] = LocationType.locationTypeEncoder
  implicit val _encDigitalLocation: Encoder[DigitalLocation] =
    deriveConfiguredEncoder
  implicit val _encPhysicalLocation: Encoder[PhysicalLocation] =
    deriveConfiguredEncoder
  implicit val _encLocation: Encoder[Location] = deriveConfiguredEncoder

  implicit val _enc11
    : Encoder[MergeCandidate[ImageData[IdState.Identifiable]]] =
    deriveConfiguredEncoder
  implicit val _enc12: Encoder[MergeCandidate[ImageData[IdState.Identified]]] =
    deriveConfiguredEncoder
  implicit val _enc13: Encoder[MatcherResult] = deriveConfiguredEncoder
  implicit val _enc14: Encoder[Person[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc15: Encoder[Person[IdState.Minted]] = deriveConfiguredEncoder
  implicit val _enc16: Encoder[Meeting[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc17: Encoder[Meeting[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc18: Encoder[Organisation[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc19: Encoder[Organisation[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc20: Encoder[Agent[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc21: Encoder[Agent[IdState.Minted]] = deriveConfiguredEncoder
  implicit val _enc22: Encoder[AbstractAgent[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc23: Encoder[AbstractAgent[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc24: Encoder[Place[IdState.Unminted]] =
    deriveConfiguredEncoder

  implicit val _encPeriodMinted: Encoder[Period[IdState.Minted]] = deriveConfiguredEncoder
  implicit val _encPeriodUnminted: Encoder[Period[IdState.Unminted]] = deriveConfiguredEncoder

  implicit val _enc26: Encoder[Concept[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc27: Encoder[Concept[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc28: Encoder[AbstractConcept[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc29: Encoder[AbstractConcept[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc30: Encoder[AbstractRootConcept[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc31: Encoder[AbstractRootConcept[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc32: Encoder[Genre[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc33: Encoder[Genre[IdState.Minted]] = deriveConfiguredEncoder
  implicit val _enc34: Encoder[Subject[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc35: Encoder[Subject[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc36: Encoder[ProductionEvent[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc37: Encoder[ProductionEvent[IdState.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc38: Encoder[Item[IdState.Unminted]] = deriveConfiguredEncoder
  implicit val _enc39: Encoder[Item[IdState.Minted]] = deriveConfiguredEncoder
  implicit val _enc40: Encoder[Contributor[IdState.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc41: Encoder[Contributor[IdState.Minted]] =
    deriveConfiguredEncoder

  implicit val _encFormat: Encoder[Format] = Format.formatEncoder
  implicit val _encHoldings: Encoder[Holdings] = deriveConfiguredEncoder
  implicit val _encWorkType: Encoder[WorkType] = WorkType.workTypeEncoder
  implicit val _encCollectionPath: Encoder[CollectionPath] =
    deriveConfiguredEncoder
  implicit val _encRelation: Encoder[Relation] = deriveConfiguredEncoder
  implicit val _encRelations: Encoder[Relations] = deriveConfiguredEncoder

  implicit val _envDerivedWorkData: Encoder[DerivedWorkData] =
    deriveConfiguredEncoder
  implicit val _envInferredData: Encoder[InferredData] =
    deriveConfiguredEncoder
  implicit val _envDerivedImageData: Encoder[DerivedImageData] =
    deriveConfiguredEncoder

  implicit val _envImageDataIdentifiable: Encoder[ImageData[IdState.Identifiable]] =
    deriveConfiguredEncoder
  implicit val _envImageDataIdentified: Encoder[ImageData[IdState.Identified]] =
    deriveConfiguredEncoder

  implicit val _enc45: Encoder[WorkData[DataState.Unidentified]] =
    deriveConfiguredEncoder
  implicit val _enc46: Encoder[WorkData[DataState.Identified]] =
    deriveConfiguredEncoder

  implicit val _encInvisibilityReason: Encoder[InvisibilityReason] =
    deriveConfiguredEncoder
  implicit val _encDeletedReason: Encoder[DeletedReason] =
    deriveConfiguredEncoder

  implicit val _envAvailability: Encoder[Availability] =
    Availability.availabilityEncoder

  implicit val _encWorkStateSource: Encoder[WorkState.Source] =
    deriveConfiguredEncoder
  implicit val _encWorkStateMerged: Encoder[WorkState.Merged] =
    deriveConfiguredEncoder
  implicit val _encWorkStateDenormalised: Encoder[WorkState.Denormalised] =
    deriveConfiguredEncoder
  implicit val _encWorkStateIdentified: Encoder[WorkState.Identified] =
    deriveConfiguredEncoder
  implicit val _encWorkStateIndexed: Encoder[WorkState.Indexed] =
    deriveConfiguredEncoder

  implicit val _encWorkVisibleSource: Encoder[Work.Visible[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkVisibleMerged: Encoder[Work.Visible[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkVisibleDenormalised: Encoder[Work.Visible[WorkState.Denormalised]] =
    deriveConfiguredEncoder
  implicit val _encWorkVisibleIdentified: Encoder[Work.Visible[WorkState.Identified]] =
    deriveConfiguredEncoder
  implicit val _encWorkVisibleIndexed: Encoder[Work.Visible[WorkState.Indexed]] =
    deriveConfiguredEncoder

  implicit val _encWorkInvisibleSource: Encoder[Work.Invisible[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkInvisibleMerged: Encoder[Work.Invisible[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkInvisibleDenormalised: Encoder[Work.Invisible[WorkState.Denormalised]] =
    deriveConfiguredEncoder
  implicit val _encWorkInvisibleIdentified: Encoder[Work.Invisible[WorkState.Identified]] =
    deriveConfiguredEncoder
  implicit val _encWorkInvisibleIndexed: Encoder[Work.Invisible[WorkState.Indexed]] =
    deriveConfiguredEncoder

  implicit val _encWorkRedirectedSource: Encoder[Work.Redirected[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkRedirectedMerged: Encoder[Work.Redirected[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkRedirectedDenormalised: Encoder[Work.Redirected[WorkState.Denormalised]] =
    deriveConfiguredEncoder
  implicit val _encWorkRedirectedIdentified: Encoder[Work.Redirected[WorkState.Identified]] =
    deriveConfiguredEncoder
  implicit val _encWorkRedirectedIndexed: Encoder[Work.Redirected[WorkState.Indexed]] =
    deriveConfiguredEncoder

  implicit val _encWorkDeletedSource: Encoder[Work.Deleted[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkDeletedMerged: Encoder[Work.Deleted[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkDeletedDenormalised: Encoder[Work.Deleted[WorkState.Denormalised]] =
    deriveConfiguredEncoder
  implicit val _encWorkDeletedIdentified: Encoder[Work.Deleted[WorkState.Identified]] =
    deriveConfiguredEncoder
  implicit val _encWorkDeletedIndexed: Encoder[Work.Deleted[WorkState.Indexed]] =
    deriveConfiguredEncoder

  implicit val _encWorkSource: Encoder[Work[WorkState.Source]] =
    deriveConfiguredEncoder
  implicit val _encWorkMerged: Encoder[Work[WorkState.Merged]] =
    deriveConfiguredEncoder
  implicit val _encWorkDenormalised: Encoder[Work[WorkState.Denormalised]] =
    deriveConfiguredEncoder
  implicit val _encWorkIdentified: Encoder[Work[WorkState.Identified]] =
    deriveConfiguredEncoder
  implicit val _encWorkIndexed: Encoder[Work[WorkState.Indexed]] =
    deriveConfiguredEncoder

  implicit val _enc63: Encoder[ParentWorks] =
    deriveConfiguredEncoder
  implicit val _enc64: Encoder[ImageSource] =
    deriveConfiguredEncoder
  implicit val _enc65: Encoder[Image[ImageState.Initial]] =
    deriveConfiguredEncoder
  implicit val _enc66: Encoder[Image[ImageState.Augmented]] =
    deriveConfiguredEncoder
  implicit val _enc67: Encoder[Image[ImageState.Indexed]] =
    deriveConfiguredEncoder
}
