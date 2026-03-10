package weco.pipeline.merger.tools

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.json.JsonUtil._
import weco.pipeline.matcher.models.WorkStub

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object IdentifiedIndexDecodeChecker {
  sealed trait DecodeMode {
    val name: String
  }

  object DecodeMode {
    case object Matcher extends DecodeMode {
      override val name: String = "matcher"
    }

    case object Merger extends DecodeMode {
      override val name: String = "merger"
    }

    def parse(value: String): Either[String, DecodeMode] =
      value.trim.toLowerCase match {
        case Matcher.name => Right(Matcher)
        case Merger.name  => Right(Merger)
        case other        => Left(s"Unsupported mode: $other")
      }
  }

  case class DecodeInputRecord(
    docId: String,
    sourceIdentifier: Option[String],
    document: Json
  )

  case class DecodeFailure(
    docId: String,
    sourceIdentifier: Option[String],
    error: String
  )

  case class DecodeSummary(
    mode: String,
    total: Int,
    succeeded: Int,
    failed: Int,
    failures: Seq[DecodeFailure]
  )

  private case class Config(mode: DecodeMode, input: Path, output: Path)

  def main(args: Array[String]): Unit = {
    val config = parseArgs(args) match {
      case Right(value) => value
      case Left(error) =>
        System.err.println(error)
        System.err.println(
          "Usage: --mode matcher|merger --input /path/to/input.jsonl --output /path/to/output.json"
        )
        sys.exit(1)
    }

    val summary = decodeFile(config)
    writeSummary(config.output, summary)
    println(summary.asJson.noSpaces)
  }

  private def parseArgs(args: Array[String]): Either[String, Config] = {
    val pairs = args
      .grouped(2)
      .collect {
        case Array(flag, value) if flag.startsWith("--") =>
          flag.drop(2) -> value
      }
      .toMap

    for {
      modeValue <- pairs.get("mode").toRight("Missing required --mode")
      mode <- DecodeMode.parse(modeValue)
      input <- pairs
        .get("input")
        .map(Paths.get(_))
        .toRight("Missing required --input")
      output <- pairs
        .get("output")
        .map(Paths.get(_))
        .toRight("Missing required --output")
    } yield Config(mode = mode, input = input, output = output)
  }

  private def decodeFile(config: Config): DecodeSummary = {
    val lines =
      Files.readAllLines(config.input, StandardCharsets.UTF_8).asScala.toSeq
    val records = lines.filter(_.trim.nonEmpty).zipWithIndex.map {
      case (line, index) =>
        fromJson[DecodeInputRecord](line) match {
          case Success(record) => record
          case Failure(error) =>
            throw new RuntimeException(
              s"Failed to parse input line ${index + 1}: ${error.getMessage}",
              error
            )
        }
    }

    val failures = records.flatMap {
      record =>
        decodeDocument(config.mode, record.document) match {
          case Success(_) => None
          case Failure(error) =>
            Some(
              DecodeFailure(
                docId = record.docId,
                sourceIdentifier = record.sourceIdentifier,
                error = error.getMessage
              )
            )
        }
    }

    DecodeSummary(
      mode = config.mode.name,
      total = records.size,
      succeeded = records.size - failures.size,
      failed = failures.size,
      failures = failures
    )
  }

  private def decodeDocument(mode: DecodeMode, json: Json): Try[Any] =
    mode match {
      case DecodeMode.Matcher => fromJson[WorkStub](json.noSpaces)
      case DecodeMode.Merger  => fromJson[Work[Identified]](json.noSpaces)
    }

  private def writeSummary(path: Path, summary: DecodeSummary): Unit = {
    val parent = path.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    Files.write(path, summary.asJson.spaces2.getBytes(StandardCharsets.UTF_8))
  }

  implicit private val decodeFailureEncoder: Encoder[DecodeFailure] =
    Encoder.forProduct3(
      "docId",
      "sourceIdentifier",
      "error"
    )(failure => (failure.docId, failure.sourceIdentifier, failure.error))

  implicit private val decodeSummaryEncoder: Encoder[DecodeSummary] =
    Encoder.forProduct5(
      "mode",
      "total",
      "succeeded",
      "failed",
      "failures"
    )(
      summary =>
        (
          summary.mode,
          summary.total,
          summary.succeeded,
          summary.failed,
          summary.failures
        )
    )

  implicit val decodeInputRecordDecoder: Decoder[DecodeInputRecord] =
    Decoder.forProduct3(
      "docId",
      "sourceIdentifier",
      "document"
    )(DecodeInputRecord.apply)

  // Keep this decoder local to the harness: WorkStub doesn't expose a shared
  // decoder, and generic derivation here previously looked for `workType`
  // instead of the Elasticsearch field `type`. This mirrors the matcher
  // payload shape directly so the harness tests the real source fields.
  implicit private val workStubDecoder: Decoder[WorkStub] =
    Decoder.forProduct3("state", "version", "type") {
      (state: Identified, version: Int, workType: String) =>
        WorkStub(state, version, workType)
    }
}
