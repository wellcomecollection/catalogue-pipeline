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
import scala.collection.mutable
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

    case object Compare extends DecodeMode {
      override val name: String = "compare"
    }

    def parse(value: String): Either[String, DecodeMode] =
      value.trim.toLowerCase match {
        case Matcher.name => Right(Matcher)
        case Merger.name  => Right(Merger)
        case Compare.name => Right(Compare)
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

  case class CompareInputRecord(
    docId: String,
    sourceIdentifier: Option[String],
    targetDocument: Json,
    baselineDocument: Json
  )

  case class JsonDiff(
    path: String,
    target: Json,
    baseline: Json
  )

  case class CompareFailure(
    docId: String,
    sourceIdentifier: Option[String],
    failureType: String,
    error: String,
    diffs: Seq[JsonDiff] = Seq.empty
  )

  case class CompareSummary(
    mode: String,
    total: Int,
    matched: Int,
    failed: Int,
    failures: Seq[CompareFailure]
  )

  private case class Config(mode: DecodeMode, input: Path, output: Path)

  def main(args: Array[String]): Unit = {
    val config = parseArgs(args) match {
      case Right(value) => value
      case Left(error) =>
        System.err.println(error)
        System.err.println(
          "Usage: --mode matcher|merger|compare --input /path/to/input.jsonl --output /path/to/output.json"
        )
        sys.exit(1)
    }

    config.mode match {
      case DecodeMode.Compare =>
        val summary = compareFile(config)
        writeSummary(config.output, summary.asJson)
        println(summary.asJson.noSpaces)
      case _ =>
        val summary = decodeFile(config)
        writeSummary(config.output, summary.asJson)
        println(summary.asJson.noSpaces)
    }
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
    val lineStream = Files.lines(config.input, StandardCharsets.UTF_8)

    try {
      var total = 0
      var succeeded = 0
      val failuresBuffer = mutable.ListBuffer[DecodeFailure]()

      lineStream
        .iterator()
        .asScala
        .filter(_.trim.nonEmpty)
        .zipWithIndex
        .foreach {
          case (line, index) =>
            val record = fromJson[DecodeInputRecord](line) match {
              case Success(r) => r
              case Failure(error) =>
                throw new RuntimeException(
                  s"Failed to parse input line ${index + 1}: ${error.getMessage}",
                  error
                )
            }

            total += 1

            decodeDocument(config.mode, record.document) match {
              case Success(_) =>
                succeeded += 1
              case Failure(error) =>
                failuresBuffer += DecodeFailure(
                  docId = record.docId,
                  sourceIdentifier = record.sourceIdentifier,
                  error = error.getMessage
                )
            }
        }

      val failures = failuresBuffer.toSeq

      DecodeSummary(
        mode = config.mode.name,
        total = total,
        succeeded = succeeded,
        failed = failures.size,
        failures = failures
      )
    } finally {
      lineStream.close()
    }
  }

  private def decodeDocument(mode: DecodeMode, json: Json): Try[Any] =
    mode match {
      case DecodeMode.Matcher => fromJson[WorkStub](json.noSpaces)
      case DecodeMode.Merger  => fromJson[Work[Identified]](json.noSpaces)
      case DecodeMode.Compare => fromJson[Work[Identified]](json.noSpaces)
    }

  private def jsonDiff(
    target: Json,
    baseline: Json,
    path: String = ""
  ): Seq[JsonDiff] = {
    if (target == baseline) {
      Seq.empty
    } else if (target.isObject && baseline.isObject) {
      val tObj = target.asObject.get
      val bObj = baseline.asObject.get
      val allKeys = (tObj.keys ++ bObj.keys).toSeq.distinct
      allKeys.flatMap {
        key =>
          val childPath = if (path.isEmpty) key else s"$path.$key"
          (tObj(key), bObj(key)) match {
            case (Some(tv), Some(bv)) => jsonDiff(tv, bv, childPath)
            case (Some(tv), None)     => Seq(JsonDiff(childPath, tv, Json.Null))
            case (None, Some(bv))     => Seq(JsonDiff(childPath, Json.Null, bv))
            case _                    => Seq.empty
          }
      }
    } else if (target.isArray && baseline.isArray) {
      val tArr = target.asArray.get
      val bArr = baseline.asArray.get
      val maxLen = math.max(tArr.size, bArr.size)
      (0 until maxLen).flatMap {
        i =>
          val childPath = s"$path[$i]"
          (tArr.lift(i), bArr.lift(i)) match {
            case (Some(tv), Some(bv)) => jsonDiff(tv, bv, childPath)
            case (Some(tv), None)     => Seq(JsonDiff(childPath, tv, Json.Null))
            case (None, Some(bv))     => Seq(JsonDiff(childPath, Json.Null, bv))
            case _                    => Seq.empty
          }
      }
    } else {
      Seq(JsonDiff(path, target, baseline))
    }
  }

  private val ignoredDiffPaths: Set[String] =
    Set("version", "state.sourceModifiedTime")

  private def compareFile(config: Config): CompareSummary = {
    val lineStream = Files.lines(config.input, StandardCharsets.UTF_8)

    try {
      var total = 0
      var failedCount = 0
      val failuresBuffer = mutable.ListBuffer[CompareFailure]()

      lineStream
        .iterator()
        .asScala
        .filter(_.trim.nonEmpty)
        .zipWithIndex
        .foreach {
          case (line, index) =>
            val record = fromJson[CompareInputRecord](line) match {
              case Success(r) => r
              case Failure(error) =>
                throw new RuntimeException(
                  s"Failed to parse compare input line ${index + 1}: ${error.getMessage}",
                  error
                )
            }

            total += 1

            val targetResult =
              fromJson[Work[Identified]](record.targetDocument.noSpaces)
            val baselineResult =
              fromJson[Work[Identified]](record.baselineDocument.noSpaces)

            val maybeFailure: Option[CompareFailure] =
              (targetResult, baselineResult) match {
                case (Failure(err), _) =>
                  Some(
                    CompareFailure(
                      docId = record.docId,
                      sourceIdentifier = record.sourceIdentifier,
                      failureType = "target_decode",
                      error = err.getMessage
                    )
                  )
                case (_, Failure(err)) =>
                  Some(
                    CompareFailure(
                      docId = record.docId,
                      sourceIdentifier = record.sourceIdentifier,
                      failureType = "baseline_decode",
                      error = err.getMessage
                    )
                  )
                case (Success(targetWork), Success(baselineWork)) =>
                  if (targetWork == baselineWork) {
                    None
                  } else {
                    val diffs =
                      jsonDiff(targetWork.asJson, baselineWork.asJson)
                        .filterNot(d => ignoredDiffPaths.contains(d.path))
                    if (diffs.isEmpty) {
                      None
                    } else {
                      val summary = diffs
                        .take(10)
                        .map(
                          d =>
                            s"${d.path}: ${d.target.noSpaces} vs ${d.baseline.noSpaces}"
                        )
                        .mkString("; ")
                      Some(
                        CompareFailure(
                          docId = record.docId,
                          sourceIdentifier = record.sourceIdentifier,
                          failureType = "mismatch",
                          error = summary,
                          diffs = diffs
                        )
                      )
                    }
                  }
              }

            maybeFailure.foreach { failure =>
              failedCount += 1
              failuresBuffer += failure
            }
        }

      val failures = failuresBuffer.toSeq

      CompareSummary(
        mode = config.mode.name,
        total = total,
        matched = total - failedCount,
        failed = failedCount,
        failures = failures
      )
    } finally {
      lineStream.close()
    }
  }

  private def writeSummary(path: Path, json: Json): Unit = {
    val parent = path.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    Files.write(path, json.spaces2.getBytes(StandardCharsets.UTF_8))
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

  implicit private val compareInputRecordDecoder: Decoder[CompareInputRecord] =
    Decoder.forProduct4(
      "docId",
      "sourceIdentifier",
      "targetDocument",
      "baselineDocument"
    )(CompareInputRecord.apply)

  implicit private val jsonDiffEncoder: Encoder[JsonDiff] =
    Encoder.forProduct3(
      "path",
      "target",
      "baseline"
    )(d => (d.path, d.target, d.baseline))

  implicit private val compareFailureEncoder: Encoder[CompareFailure] =
    Encoder.forProduct5(
      "docId",
      "sourceIdentifier",
      "failureType",
      "error",
      "diffs"
    )(f => (f.docId, f.sourceIdentifier, f.failureType, f.error, f.diffs))

  implicit private val compareSummaryEncoder: Encoder[CompareSummary] =
    Encoder.forProduct5(
      "mode",
      "total",
      "matched",
      "failed",
      "failures"
    )(s => (s.mode, s.total, s.matched, s.failed, s.failures))

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
