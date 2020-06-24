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
  implicit val _dec03: Decoder[Unminted] = deriveConfiguredDecoder
  implicit val _dec04: Decoder[Minted] = deriveConfiguredDecoder
  implicit val _dec05: Decoder[InstantRange] = deriveConfiguredDecoder
  implicit val _dec06: Decoder[DigitalLocation] = deriveConfiguredDecoder
  implicit val _dec07: Decoder[PhysicalLocation] = deriveConfiguredDecoder
  implicit val _dec08: Decoder[Location] = deriveConfiguredDecoder
  implicit val _dec11: Decoder[MergeCandidate] = deriveConfiguredDecoder
  implicit val _dec12: Decoder[MatcherResult] = deriveConfiguredDecoder
  implicit val _dec13: Decoder[Person[Unminted]] = deriveConfiguredDecoder
  implicit val _dec14: Decoder[Person[Minted]] = deriveConfiguredDecoder
  implicit val _dec15: Decoder[Meeting[Unminted]] = deriveConfiguredDecoder
  implicit val _dec16: Decoder[Meeting[Minted]] = deriveConfiguredDecoder
  implicit val _dec17: Decoder[Organisation[Unminted]] = deriveConfiguredDecoder
  implicit val _dec18: Decoder[Organisation[Minted]] = deriveConfiguredDecoder
  implicit val _dec19: Decoder[Agent[Unminted]] = deriveConfiguredDecoder
  implicit val _dec20: Decoder[Agent[Minted]] = deriveConfiguredDecoder
  implicit val _dec21: Decoder[AbstractAgent[Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec22: Decoder[AbstractAgent[Minted]] = deriveConfiguredDecoder
  implicit val _dec23: Decoder[Place[Unminted]] = deriveConfiguredDecoder
  implicit val _dec24: Decoder[Period[Minted]] = deriveConfiguredDecoder
  implicit val _dec25: Decoder[Concept[Unminted]] = deriveConfiguredDecoder
  implicit val _dec26: Decoder[Concept[Minted]] = deriveConfiguredDecoder
  implicit val _dec27: Decoder[AbstractConcept[Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec28: Decoder[AbstractConcept[Minted]] =
    deriveConfiguredDecoder
  implicit val _dec29: Decoder[AbstractRootConcept[Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec30: Decoder[AbstractRootConcept[Minted]] =
    deriveConfiguredDecoder
  implicit val _dec31: Decoder[Genre[Unminted]] = deriveConfiguredDecoder
  implicit val _dec32: Decoder[Genre[Minted]] = deriveConfiguredDecoder
  implicit val _dec33: Decoder[Subject[Unminted]] = deriveConfiguredDecoder
  implicit val _dec34: Decoder[Subject[Minted]] = deriveConfiguredDecoder
  implicit val _dec35: Decoder[ProductionEvent[Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec36: Decoder[ProductionEvent[Minted]] =
    deriveConfiguredDecoder
  implicit val _dec37: Decoder[Item[Unminted]] = deriveConfiguredDecoder
  implicit val _dec38: Decoder[Item[Minted]] = deriveConfiguredDecoder
  implicit val _dec39: Decoder[Contributor[Unminted]] = deriveConfiguredDecoder
  implicit val _dec40: Decoder[Contributor[Minted]] = deriveConfiguredDecoder
  implicit val _dec41: Decoder[WorkData[Unminted, Identifiable]] =
    deriveConfiguredDecoder
  implicit val _dec42: Decoder[WorkData[Minted, Identified]] =
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
  implicit val _dec51: Decoder[UnmergedImage[Identifiable, Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec52: Decoder[UnmergedImage[Identified, Minted]] =
    deriveConfiguredDecoder


  implicit val feg: Decoder[ImageSource[Identifiable, Unminted]] = deriveConfiguredDecoder
  implicit val dshd: Decoder[ImageSource[Identified, Minted]] = deriveConfiguredDecoder

  implicit val _dec53: Decoder[MergedImage[Identifiable, Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec54: Decoder[MergedImage[Identified, Minted]] =
    deriveConfiguredDecoder
  implicit val _dec55: Decoder[BaseImage[Identifiable, Unminted]] =
    deriveConfiguredDecoder
  implicit val _dec56: Decoder[BaseImage[Identified, Minted]] = deriveConfiguredDecoder
  implicit val _dec57: Decoder[AugmentedImage] = deriveConfiguredDecoder

  implicit val _enc00: Encoder[AccessCondition] = deriveConfiguredEncoder
  implicit val _enc01: Encoder[Note] = deriveConfiguredEncoder
  implicit val _enc02: Encoder[SourceIdentifier] = deriveConfiguredEncoder
  implicit val _enc03: Encoder[Unminted] = deriveConfiguredEncoder
  implicit val _enc04: Encoder[Minted] = deriveConfiguredEncoder
  implicit val _enc05: Encoder[InstantRange] = deriveConfiguredEncoder
  implicit val _enc06: Encoder[DigitalLocation] = deriveConfiguredEncoder
  implicit val _enc07: Encoder[PhysicalLocation] = deriveConfiguredEncoder
  implicit val _enc08: Encoder[Location] = deriveConfiguredEncoder
  implicit val _enc11: Encoder[MergeCandidate] = deriveConfiguredEncoder
  implicit val _enc12: Encoder[MatcherResult] = deriveConfiguredEncoder
  implicit val _enc13: Encoder[Person[Unminted]] = deriveConfiguredEncoder
  implicit val _enc14: Encoder[Person[Minted]] = deriveConfiguredEncoder
  implicit val _enc15: Encoder[Meeting[Unminted]] = deriveConfiguredEncoder
  implicit val _enc16: Encoder[Meeting[Minted]] = deriveConfiguredEncoder
  implicit val _enc17: Encoder[Organisation[Unminted]] = deriveConfiguredEncoder
  implicit val _enc18: Encoder[Organisation[Minted]] = deriveConfiguredEncoder
  implicit val _enc19: Encoder[Agent[Unminted]] = deriveConfiguredEncoder
  implicit val _enc20: Encoder[Agent[Minted]] = deriveConfiguredEncoder
  implicit val _enc21: Encoder[AbstractAgent[Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc22: Encoder[AbstractAgent[Minted]] = deriveConfiguredEncoder
  implicit val _enc23: Encoder[Place[Unminted]] = deriveConfiguredEncoder
  implicit val _enc24: Encoder[Period[Minted]] = deriveConfiguredEncoder
  implicit val _enc25: Encoder[Concept[Unminted]] = deriveConfiguredEncoder
  implicit val _enc26: Encoder[Concept[Minted]] = deriveConfiguredEncoder
  implicit val _enc27: Encoder[AbstractConcept[Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc28: Encoder[AbstractConcept[Minted]] =
    deriveConfiguredEncoder
  implicit val _enc29: Encoder[AbstractRootConcept[Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc30: Encoder[AbstractRootConcept[Minted]] =
    deriveConfiguredEncoder
  implicit val _enc31: Encoder[Genre[Unminted]] = deriveConfiguredEncoder
  implicit val _enc32: Encoder[Genre[Minted]] = deriveConfiguredEncoder
  implicit val _enc33: Encoder[Subject[Unminted]] = deriveConfiguredEncoder
  implicit val _enc34: Encoder[Subject[Minted]] = deriveConfiguredEncoder
  implicit val _enc35: Encoder[ProductionEvent[Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc36: Encoder[ProductionEvent[Minted]] =
    deriveConfiguredEncoder
  implicit val _enc37: Encoder[Item[Unminted]] = deriveConfiguredEncoder
  implicit val _enc38: Encoder[Item[Minted]] = deriveConfiguredEncoder
  implicit val _enc39: Encoder[Contributor[Unminted]] = deriveConfiguredEncoder
  implicit val _enc40: Encoder[Contributor[Minted]] = deriveConfiguredEncoder
  implicit val _enc41: Encoder[WorkData[Unminted, Identifiable]] =
    deriveConfiguredEncoder
  implicit val _enc42: Encoder[WorkData[Minted, Identified]] =
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
  implicit val _enc51: Encoder[UnmergedImage[Identifiable, Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc52: Encoder[UnmergedImage[Identified, Minted]] =
    deriveConfiguredEncoder
  implicit val _enc53: Encoder[MergedImage[Identifiable, Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc54: Encoder[MergedImage[Identified, Minted]] =
    deriveConfiguredEncoder
  implicit val _enc55: Encoder[BaseImage[Identifiable, Unminted]] =
    deriveConfiguredEncoder
  implicit val _enc56: Encoder[BaseImage[Identified, Minted]] = deriveConfiguredEncoder
  implicit val _enc57: Encoder[AugmentedImage] = deriveConfiguredEncoder
}
