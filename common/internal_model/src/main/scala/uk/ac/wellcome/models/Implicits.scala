package uk.ac.wellcome.models

import io.circe.generic.extras.semiauto._
import io.circe.generic.{semiauto => simple}
import io.circe.java8.time.TimeInstances
import io.circe._

import scala.collection.immutable.::

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.matcher.MatcherResult
import uk.ac.wellcome.json.JsonUtil._

object Implicits extends TimeInstances {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).
  //
  // The particular implicits defined here have been chosen by generating
  // flamegraphs using the scalac-profiling plugin. See this blog post for
  // info: https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html

  implicit val _dec00: Decoder[AccessCondition] = deriveDecoder
  implicit val _dec01: Decoder[Note] = deriveDecoder
  implicit val _dec02: Decoder[SourceIdentifier] = deriveDecoder
  implicit val _dec03: Decoder[Identifiable[AbstractConcept]] = deriveDecoder
  implicit val _dec04: Decoder[Unidentifiable[AbstractConcept]] = deriveDecoder
  implicit val _dec05: Decoder[Person] = deriveDecoder
  implicit val _dec06: Decoder[InstantRange] = deriveDecoder
  implicit val _dec07: Decoder[Period] = deriveDecoder
  implicit val _dec08: Decoder[DigitalLocation] = deriveDecoder
  implicit val _dec09: Decoder[PhysicalLocation] = deriveDecoder
  implicit val _dec10: Decoder[Location] = deriveDecoder
  implicit val _dec11: Decoder[Item] = deriveDecoder
  implicit val _dec12: Decoder[MergeCandidate] = deriveDecoder
  implicit val _dec14: Decoder[MatcherResult] = deriveDecoder
  implicit val _dec15: Decoder[Concept] = deriveDecoder
  implicit val _dec16: Decoder[AbstractConcept] = deriveDecoder
  implicit val _dec17: Decoder[AbstractRootConcept] = deriveDecoder
  implicit val _dec18: Decoder[Unminted[AbstractConcept]] =
    deriveDecoder
  implicit val _dec19: Decoder[Unminted[AbstractRootConcept]] =
    deriveDecoder
  implicit val _dec20: Decoder[Unminted[AbstractAgent]] = deriveDecoder
  implicit val _dec21: Decoder[Genre[Unminted[AbstractConcept]]] =
    deriveDecoder
  implicit val _dec22: Decoder[Contributor[Unminted[AbstractAgent]]] =
    deriveDecoder
  implicit val _dec23: Decoder[ProductionEvent[Unminted[AbstractAgent]]] =
    deriveDecoder
  implicit val _dec24: Decoder[Unminted[Item]] = deriveDecoder
  implicit val _dec25: Decoder[::[Unminted[AbstractRootConcept]]] =
    simple.deriveDecoder
  implicit val _dec26: Decoder[::[Unminted[AbstractAgent]]] =
    simple.deriveDecoder
  implicit val _dec27: Decoder[Subject[Unminted[AbstractRootConcept]]] =
    deriveDecoder
  implicit val _dec28: Decoder[Minted[AbstractConcept]] = deriveDecoder
  implicit val _dec29: Decoder[Minted[AbstractRootConcept]] = deriveDecoder
  implicit val _dec30: Decoder[Minted[AbstractAgent]] = deriveDecoder
  implicit val _dec31: Decoder[Genre[Minted[AbstractConcept]]] =
    deriveDecoder
  implicit val _dec32: Decoder[Contributor[Minted[AbstractAgent]]] =
    deriveDecoder
  implicit val _dec33: Decoder[ProductionEvent[Minted[AbstractAgent]]] =
    deriveDecoder
  implicit val _dec34: Decoder[Minted[Item]] = deriveDecoder
  implicit val _dec35: Decoder[::[Minted[AbstractRootConcept]]] =
    simple.deriveDecoder
  implicit val _dec36: Decoder[::[Minted[AbstractAgent]]] =
    simple.deriveDecoder
  implicit val _dec37: Decoder[Subject[Minted[AbstractRootConcept]]] =
    deriveDecoder
  implicit val _dec38: Decoder[Identified[AbstractConcept]] = deriveDecoder
  implicit val _dec39: Decoder[Identified[AbstractRootConcept]] = deriveDecoder
  implicit val _dec40: Decoder[Identified[AbstractAgent]] = deriveDecoder
  implicit val _dec41: Decoder[Genre[Identified[AbstractConcept]]] =
    deriveDecoder
  implicit val _dec42: Decoder[Contributor[Identified[AbstractAgent]]] =
    deriveDecoder
  implicit val _dec43: Decoder[ProductionEvent[Identified[AbstractAgent]]] =
    deriveDecoder
  implicit val _dec44: Decoder[Identified[Item]] = deriveDecoder
  implicit val _dec45: Decoder[::[Identified[AbstractRootConcept]]] =
    simple.deriveDecoder
  implicit val _dec46: Decoder[::[Identified[AbstractAgent]]] =
    simple.deriveDecoder
  implicit val _dec47: Decoder[Subject[Identified[AbstractRootConcept]]] =
    deriveDecoder
  implicit val _dec48: Decoder[UnidentifiedWork] = deriveDecoder
  implicit val _dec49: Decoder[UnidentifiedInvisibleWork] = deriveDecoder
  implicit val _dec50: Decoder[IdentifiedWork] = deriveDecoder
  implicit val _dec51: Decoder[UnidentifiedRedirectedWork] = deriveDecoder
  implicit val _dec52: Decoder[TransformedBaseWork] = deriveDecoder
  implicit val _dec53: Decoder[IdentifiedBaseWork] = deriveDecoder
  implicit val _dec54: Decoder[BaseWork] = deriveDecoder
  implicit val _dec55: Decoder[SierraTransformable] = deriveDecoder

  implicit val _enc00: Encoder[AccessCondition] = deriveEncoder
  implicit val _enc01: Encoder[Note] = deriveEncoder
  implicit val _enc02: Encoder[SourceIdentifier] = deriveEncoder
  implicit val _enc03: Encoder[Identifiable[AbstractConcept]] = deriveEncoder
  implicit val _enc04: Encoder[Unidentifiable[AbstractConcept]] = deriveEncoder
  implicit val _enc05: Encoder[Person] = deriveEncoder
  implicit val _enc06: Encoder[InstantRange] = deriveEncoder
  implicit val _enc07: Encoder[Period] = deriveEncoder
  implicit val _enc08: Encoder[DigitalLocation] = deriveEncoder
  implicit val _enc09: Encoder[PhysicalLocation] = deriveEncoder
  implicit val _enc10: Encoder[Location] = deriveEncoder
  implicit val _enc11: Encoder[Item] = deriveEncoder
  implicit val _enc12: Encoder[MergeCandidate] = deriveEncoder
  implicit val _enc14: Encoder[MatcherResult] = deriveEncoder
  implicit val _enc15: Encoder[Concept] = deriveEncoder
  implicit val _enc16: Encoder[AbstractConcept] = deriveEncoder
  implicit val _enc17: Encoder[AbstractRootConcept] = deriveEncoder
  implicit val _enc18: Encoder[Unminted[AbstractConcept]] =
    deriveEncoder
  implicit val _enc19: Encoder[Unminted[AbstractRootConcept]] =
    deriveEncoder
  implicit val _enc20: Encoder[Unminted[AbstractAgent]] = deriveEncoder
  implicit val _enc21: Encoder[Genre[Unminted[AbstractConcept]]] =
    deriveEncoder
  implicit val _enc22: Encoder[Contributor[Unminted[AbstractAgent]]] =
    deriveEncoder
  implicit val _enc23: Encoder[ProductionEvent[Unminted[AbstractAgent]]] =
    deriveEncoder
  implicit val _enc24: Encoder[Unminted[Item]] = deriveEncoder
  implicit val _enc25: Encoder[::[Unminted[AbstractRootConcept]]] =
    simple.deriveEncoder
  implicit val _enc26: Encoder[::[Unminted[AbstractAgent]]] =
    simple.deriveEncoder
  implicit val _enc27: Encoder[Subject[Unminted[AbstractRootConcept]]] =
    deriveEncoder
  implicit val _enc28: Encoder[Minted[AbstractConcept]] = deriveEncoder
  implicit val _enc29: Encoder[Minted[AbstractRootConcept]] = deriveEncoder
  implicit val _enc30: Encoder[Minted[AbstractAgent]] = deriveEncoder
  implicit val _enc31: Encoder[Genre[Minted[AbstractConcept]]] =
    deriveEncoder
  implicit val _enc32: Encoder[Contributor[Minted[AbstractAgent]]] =
    deriveEncoder
  implicit val _enc33: Encoder[ProductionEvent[Minted[AbstractAgent]]] =
    deriveEncoder
  implicit val _enc34: Encoder[Minted[Item]] = deriveEncoder
  implicit val _enc35: Encoder[::[Minted[AbstractRootConcept]]] =
    simple.deriveEncoder
  implicit val _enc36: Encoder[::[Minted[AbstractAgent]]] =
    simple.deriveEncoder
  implicit val _enc37: Encoder[Subject[Minted[AbstractRootConcept]]] =
    deriveEncoder
  implicit val _enc38: Encoder[Identified[AbstractConcept]] = deriveEncoder
  implicit val _enc39: Encoder[Identified[AbstractRootConcept]] = deriveEncoder
  implicit val _enc40: Encoder[Identified[AbstractAgent]] = deriveEncoder
  implicit val _enc41: Encoder[Genre[Identified[AbstractConcept]]] =
    deriveEncoder
  implicit val _enc42: Encoder[Contributor[Identified[AbstractAgent]]] =
    deriveEncoder
  implicit val _enc43: Encoder[ProductionEvent[Identified[AbstractAgent]]] =
    deriveEncoder
  implicit val _enc44: Encoder[Identified[Item]] = deriveEncoder
  implicit val _enc45: Encoder[::[Identified[AbstractRootConcept]]] =
    simple.deriveEncoder
  implicit val _enc46: Encoder[::[Identified[AbstractAgent]]] =
    simple.deriveEncoder
  implicit val _enc47: Encoder[Subject[Identified[AbstractRootConcept]]] =
    deriveEncoder
  implicit val _enc48: Encoder[UnidentifiedWork] = deriveEncoder
  implicit val _enc49: Encoder[UnidentifiedInvisibleWork] = deriveEncoder
  implicit val _enc50: Encoder[IdentifiedWork] = deriveEncoder
  implicit val _enc51: Encoder[UnidentifiedRedirectedWork] = deriveEncoder
  implicit val _enc52: Encoder[TransformedBaseWork] = deriveEncoder
  implicit val _enc53: Encoder[IdentifiedBaseWork] = deriveEncoder
  implicit val _enc54: Encoder[BaseWork] = deriveEncoder
  implicit val _enc55: Encoder[SierraTransformable] = deriveEncoder
}
