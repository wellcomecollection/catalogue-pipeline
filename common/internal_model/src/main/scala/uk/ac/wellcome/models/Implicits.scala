package uk.ac.wellcome.models

import io.circe.generic.extras.semiauto._
import io.circe._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.matcher.MatcherResult
import uk.ac.wellcome.json.JsonUtil._

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).
  //
  // The particular implicits defined here have been chosen by generating
  // flamegraphs using the scalac-profiling plugin. See this blog post for
  // info: https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html

  implicit val _dec00: Decoder[AccessCondition] = deriveConfiguredDecoder
  implicit val _dec01: Decoder[Note] = deriveConfiguredDecoder
  implicit val _dec02: Decoder[SourceIdentifier] = deriveConfiguredDecoder
  implicit val _dec03: Decoder[Id.Unminted] = deriveConfiguredDecoder
  implicit val _dec04: Decoder[Id.Minted] = deriveConfiguredDecoder
  implicit val _dec05: Decoder[InstantRange] = deriveConfiguredDecoder
  implicit val _dec06: Decoder[DigitalLocationDeprecated] =
    deriveConfiguredDecoder
  implicit val _dec07: Decoder[PhysicalLocationDeprecated] =
    deriveConfiguredDecoder
  implicit val _dec08: Decoder[LocationDeprecated] = deriveConfiguredDecoder
  implicit val _dec11: Decoder[MergeCandidate] = deriveConfiguredDecoder
  implicit val _dec12: Decoder[MatcherResult] = deriveConfiguredDecoder
  implicit val _dec13: Decoder[Person[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec14: Decoder[Person[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec15: Decoder[Meeting[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec16: Decoder[Meeting[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec17: Decoder[Organisation[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec18: Decoder[Organisation[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec19: Decoder[Agent[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec20: Decoder[Agent[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec21: Decoder[AbstractAgent[Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec22: Decoder[AbstractAgent[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec23: Decoder[Place[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec24: Decoder[Period[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec25: Decoder[Concept[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec26: Decoder[Concept[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec27: Decoder[AbstractConcept[Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec28: Decoder[AbstractConcept[Id.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec29: Decoder[AbstractRootConcept[Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec30: Decoder[AbstractRootConcept[Id.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec31: Decoder[Genre[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec32: Decoder[Genre[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec33: Decoder[Subject[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec34: Decoder[Subject[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec35: Decoder[ProductionEvent[Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec36: Decoder[ProductionEvent[Id.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec37: Decoder[Item[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec38: Decoder[Item[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec39: Decoder[Contributor[Id.Unminted]] = deriveConfiguredDecoder
  implicit val _dec40: Decoder[Contributor[Id.Minted]] = deriveConfiguredDecoder
  implicit val _dec41: Decoder[WorkData[Id.Unminted, Id.Identifiable]] =
    deriveConfiguredDecoder
  implicit val _dec42: Decoder[WorkData[Id.Minted, Id.Identified]] =
    deriveConfiguredDecoder
  implicit val _dec43: Decoder[UnidentifiedWork] = deriveConfiguredDecoder
  implicit val _dec44: Decoder[UnidentifiedInvisibleWork] =
    deriveConfiguredDecoder
  implicit val _dec45: Decoder[IdentifiedWork] = deriveConfiguredDecoder
  implicit val _dec46: Decoder[UnidentifiedRedirectedWork] =
    deriveConfiguredDecoder
  implicit val _dec47: Decoder[TransformedBaseWork] = deriveConfiguredDecoder
  implicit val _dec48: Decoder[IdentifiedBaseWork] = deriveConfiguredDecoder
  implicit val _dec49: Decoder[BaseWork] = deriveConfiguredDecoder
  implicit val _dec51: Decoder[UnmergedImage[Id.Identifiable, Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec52: Decoder[UnmergedImage[Id.Identified, Id.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec53: Decoder[MergedImage[Id.Identifiable, Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec54: Decoder[MergedImage[Id.Identified, Id.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec55: Decoder[BaseImage[Id.Identifiable, Id.Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec56: Decoder[BaseImage[Id.Identified, Id.Minted]] =
    deriveConfiguredDecoder
  implicit val _dec57: Decoder[AugmentedImage] = deriveConfiguredDecoder

  implicit val _enc00: Encoder[AccessCondition] = deriveConfiguredEncoder
  implicit val _enc01: Encoder[Note] = deriveConfiguredEncoder
  implicit val _enc02: Encoder[SourceIdentifier] = deriveConfiguredEncoder
  implicit val _enc03: Encoder[Id.Unminted] = deriveConfiguredEncoder
  implicit val _enc04: Encoder[Id.Minted] = deriveConfiguredEncoder
  implicit val _enc05: Encoder[InstantRange] = deriveConfiguredEncoder
  implicit val _enc06: Encoder[DigitalLocationDeprecated] =
    deriveConfiguredEncoder
  implicit val _enc07: Encoder[PhysicalLocationDeprecated] =
    deriveConfiguredEncoder
  implicit val _enc08: Encoder[LocationDeprecated] = deriveConfiguredEncoder
  implicit val _enc11: Encoder[MergeCandidate] = deriveConfiguredEncoder
  implicit val _enc12: Encoder[MatcherResult] = deriveConfiguredEncoder
  implicit val _enc13: Encoder[Person[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc14: Encoder[Person[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc15: Encoder[Meeting[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc16: Encoder[Meeting[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc17: Encoder[Organisation[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc18: Encoder[Organisation[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc19: Encoder[Agent[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc20: Encoder[Agent[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc21: Encoder[AbstractAgent[Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc22: Encoder[AbstractAgent[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc23: Encoder[Place[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc24: Encoder[Period[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc25: Encoder[Concept[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc26: Encoder[Concept[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc27: Encoder[AbstractConcept[Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc28: Encoder[AbstractConcept[Id.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc29: Encoder[AbstractRootConcept[Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc30: Encoder[AbstractRootConcept[Id.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc31: Encoder[Genre[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc32: Encoder[Genre[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc33: Encoder[Subject[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc34: Encoder[Subject[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc35: Encoder[ProductionEvent[Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc36: Encoder[ProductionEvent[Id.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc37: Encoder[Item[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc38: Encoder[Item[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc39: Encoder[Contributor[Id.Unminted]] = deriveConfiguredEncoder
  implicit val _enc40: Encoder[Contributor[Id.Minted]] = deriveConfiguredEncoder
  implicit val _enc41: Encoder[WorkData[Id.Unminted, Id.Identifiable]] =
    deriveConfiguredEncoder
  implicit val _enc42: Encoder[WorkData[Id.Minted, Id.Identified]] =
    deriveConfiguredEncoder
  implicit val _enc43: Encoder[UnidentifiedWork] = deriveConfiguredEncoder
  implicit val _enc44: Encoder[UnidentifiedInvisibleWork] =
    deriveConfiguredEncoder
  implicit val _enc45: Encoder[IdentifiedWork] = deriveConfiguredEncoder
  implicit val _enc46: Encoder[UnidentifiedRedirectedWork] =
    deriveConfiguredEncoder
  implicit val _enc47: Encoder[TransformedBaseWork] = deriveConfiguredEncoder
  implicit val _enc48: Encoder[IdentifiedBaseWork] = deriveConfiguredEncoder
  implicit val _enc49: Encoder[BaseWork] = deriveConfiguredEncoder
  implicit val _enc51: Encoder[UnmergedImage[Id.Identifiable, Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc52: Encoder[UnmergedImage[Id.Identified, Id.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc53: Encoder[MergedImage[Id.Identifiable, Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc54: Encoder[MergedImage[Id.Identified, Id.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc55: Encoder[BaseImage[Id.Identifiable, Id.Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc56: Encoder[BaseImage[Id.Identified, Id.Minted]] =
    deriveConfiguredEncoder
  implicit val _enc57: Encoder[AugmentedImage] = deriveConfiguredEncoder
}
