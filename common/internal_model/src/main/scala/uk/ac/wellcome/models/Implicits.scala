package uk.ac.wellcome.models

import io.circe.generic.extras.semiauto._
import io.circe.java8.time.TimeInstances
import io.circe._

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
  implicit val _dec03: Decoder[Unminted] = deriveDecoder
  implicit val _dec04: Decoder[Minted] = deriveDecoder
  implicit val _dec05: Decoder[InstantRange] = deriveDecoder
  implicit val _dec06: Decoder[DigitalLocation] = deriveDecoder
  implicit val _dec07: Decoder[PhysicalLocation] = deriveDecoder
  implicit val _dec08: Decoder[Location] = deriveDecoder
  implicit val _dec11: Decoder[MergeCandidate] = deriveDecoder
  implicit val _dec12: Decoder[MatcherResult] = deriveDecoder
  implicit val _dec13: Decoder[Person[Unminted]] = deriveDecoder
  implicit val _dec14: Decoder[Person[Minted]] = deriveDecoder
  implicit val _dec15: Decoder[Meeting[Unminted]] = deriveDecoder
  implicit val _dec16: Decoder[Meeting[Minted]] = deriveDecoder
  implicit val _dec17: Decoder[Organisation[Unminted]] = deriveDecoder
  implicit val _dec18: Decoder[Organisation[Minted]] = deriveDecoder
  implicit val _dec19: Decoder[Agent[Unminted]] = deriveDecoder
  implicit val _dec20: Decoder[Agent[Minted]] = deriveDecoder
  implicit val _dec21: Decoder[AbstractAgent[Unminted]] = deriveDecoder
  implicit val _dec22: Decoder[AbstractAgent[Minted]] = deriveDecoder
  implicit val _dec23: Decoder[Place[Unminted]] = deriveDecoder
  implicit val _dec24: Decoder[Period[Minted]] = deriveDecoder
  implicit val _dec25: Decoder[Concept[Unminted]] = deriveDecoder
  implicit val _dec26: Decoder[Concept[Minted]] = deriveDecoder
  implicit val _dec27: Decoder[AbstractConcept[Unminted]] = deriveDecoder
  implicit val _dec28: Decoder[AbstractConcept[Minted]] = deriveDecoder
  implicit val _dec29: Decoder[AbstractRootConcept[Unminted]] = deriveDecoder
  implicit val _dec30: Decoder[AbstractRootConcept[Minted]] = deriveDecoder
  implicit val _dec31: Decoder[Genre[Unminted]] = deriveDecoder
  implicit val _dec32: Decoder[Genre[Minted]] = deriveDecoder
  implicit val _dec33: Decoder[Subject[Unminted]] = deriveDecoder
  implicit val _dec34: Decoder[Subject[Minted]] = deriveDecoder
  implicit val _dec35: Decoder[ProductionEvent[Unminted]] = deriveDecoder
  implicit val _dec36: Decoder[ProductionEvent[Minted]] = deriveDecoder
  implicit val _dec37: Decoder[Item[Unminted]] = deriveDecoder
  implicit val _dec38: Decoder[Item[Minted]] = deriveDecoder
  implicit val _dec39: Decoder[Contributor[Unminted]] = deriveDecoder
  implicit val _dec40: Decoder[Contributor[Minted]] = deriveDecoder
  implicit val _dec41: Decoder[WorkData[Unminted]] = deriveDecoder
  implicit val _dec42: Decoder[WorkData[Minted]] = deriveDecoder
  implicit val _dec43: Decoder[UnidentifiedWork] = deriveDecoder
  implicit val _dec44: Decoder[UnidentifiedInvisibleWork] = deriveDecoder
  implicit val _dec45: Decoder[IdentifiedWork] = deriveDecoder
  implicit val _dec46: Decoder[UnidentifiedRedirectedWork] = deriveDecoder
  implicit val _dec47: Decoder[TransformedBaseWork] = deriveDecoder
  implicit val _dec48: Decoder[IdentifiedBaseWork] = deriveDecoder
  implicit val _dec49: Decoder[BaseWork] = deriveDecoder
  implicit val _dec50: Decoder[SierraTransformable] = deriveDecoder
  implicit val _dec51: Decoder[UnmergedImage[Unminted]] = deriveDecoder
  implicit val _dec52: Decoder[UnmergedImage[Minted]] = deriveDecoder
  implicit val _dec53: Decoder[MergedImage[Unminted]] = deriveDecoder
  implicit val _dec54: Decoder[MergedImage[Minted]] = deriveDecoder
  implicit val _dec55: Decoder[ImageData] = deriveDecoder
  implicit val _dec56: Decoder[BaseImage[Unminted]] = deriveDecoder
  implicit val _dec57: Decoder[BaseImage[Minted]] = deriveDecoder

  implicit val _enc00: Encoder[AccessCondition] = deriveEncoder
  implicit val _enc01: Encoder[Note] = deriveEncoder
  implicit val _enc02: Encoder[SourceIdentifier] = deriveEncoder
  implicit val _enc03: Encoder[Unminted] = deriveEncoder
  implicit val _enc04: Encoder[Minted] = deriveEncoder
  implicit val _enc05: Encoder[InstantRange] = deriveEncoder
  implicit val _enc06: Encoder[DigitalLocation] = deriveEncoder
  implicit val _enc07: Encoder[PhysicalLocation] = deriveEncoder
  implicit val _enc08: Encoder[Location] = deriveEncoder
  implicit val _enc11: Encoder[MergeCandidate] = deriveEncoder
  implicit val _enc12: Encoder[MatcherResult] = deriveEncoder
  implicit val _enc13: Encoder[Person[Unminted]] = deriveEncoder
  implicit val _enc14: Encoder[Person[Minted]] = deriveEncoder
  implicit val _enc15: Encoder[Meeting[Unminted]] = deriveEncoder
  implicit val _enc16: Encoder[Meeting[Minted]] = deriveEncoder
  implicit val _enc17: Encoder[Organisation[Unminted]] = deriveEncoder
  implicit val _enc18: Encoder[Organisation[Minted]] = deriveEncoder
  implicit val _enc19: Encoder[Agent[Unminted]] = deriveEncoder
  implicit val _enc20: Encoder[Agent[Minted]] = deriveEncoder
  implicit val _enc21: Encoder[AbstractAgent[Unminted]] = deriveEncoder
  implicit val _enc22: Encoder[AbstractAgent[Minted]] = deriveEncoder
  implicit val _enc23: Encoder[Place[Unminted]] = deriveEncoder
  implicit val _enc24: Encoder[Period[Minted]] = deriveEncoder
  implicit val _enc25: Encoder[Concept[Unminted]] = deriveEncoder
  implicit val _enc26: Encoder[Concept[Minted]] = deriveEncoder
  implicit val _enc27: Encoder[AbstractConcept[Unminted]] = deriveEncoder
  implicit val _enc28: Encoder[AbstractConcept[Minted]] = deriveEncoder
  implicit val _enc29: Encoder[AbstractRootConcept[Unminted]] = deriveEncoder
  implicit val _enc30: Encoder[AbstractRootConcept[Minted]] = deriveEncoder
  implicit val _enc31: Encoder[Genre[Unminted]] = deriveEncoder
  implicit val _enc32: Encoder[Genre[Minted]] = deriveEncoder
  implicit val _enc33: Encoder[Subject[Unminted]] = deriveEncoder
  implicit val _enc34: Encoder[Subject[Minted]] = deriveEncoder
  implicit val _enc35: Encoder[ProductionEvent[Unminted]] = deriveEncoder
  implicit val _enc36: Encoder[ProductionEvent[Minted]] = deriveEncoder
  implicit val _enc37: Encoder[Item[Unminted]] = deriveEncoder
  implicit val _enc38: Encoder[Item[Minted]] = deriveEncoder
  implicit val _enc39: Encoder[Contributor[Unminted]] = deriveEncoder
  implicit val _enc40: Encoder[Contributor[Minted]] = deriveEncoder
  implicit val _enc41: Encoder[WorkData[Unminted]] = deriveEncoder
  implicit val _enc42: Encoder[WorkData[Minted]] = deriveEncoder
  implicit val _enc43: Encoder[UnidentifiedWork] = deriveEncoder
  implicit val _enc44: Encoder[UnidentifiedInvisibleWork] = deriveEncoder
  implicit val _enc45: Encoder[IdentifiedWork] = deriveEncoder
  implicit val _enc46: Encoder[UnidentifiedRedirectedWork] = deriveEncoder
  implicit val _enc47: Encoder[TransformedBaseWork] = deriveEncoder
  implicit val _enc48: Encoder[IdentifiedBaseWork] = deriveEncoder
  implicit val _enc49: Encoder[BaseWork] = deriveEncoder
  implicit val _enc50: Encoder[SierraTransformable] = deriveEncoder
  implicit val _enc51: Encoder[UnmergedImage[Unminted]] = deriveEncoder
  implicit val _enc52: Encoder[UnmergedImage[Minted]] = deriveEncoder
  implicit val _enc53: Encoder[MergedImage[Unminted]] = deriveEncoder
  implicit val _enc54: Encoder[MergedImage[Minted]] = deriveEncoder
  implicit val _enc55: Encoder[ImageData] = deriveEncoder
  implicit val _enc56: Encoder[BaseImage[Unminted]] = deriveEncoder
  implicit val _enc57: Encoder[BaseImage[Minted]] = deriveEncoder
}
