package weco.pipeline.transformer.parse

import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.text.TextNormalisation._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

/** Parses one period label per input line the way a ǂy subdivision is parsed
  * (ParsedPeriod, then PeriodOps.identifiable), writing one JSON object per
  * line. Lives in test sources so it is never shipped; run it with
  *   sbt "transformer_common/Test/runMain weco.pipeline.transformer.parse.PeriodParserCli in.txt out.jsonl"
  */
object PeriodParserCli extends LabelDerivedIdentifiers {
  private def quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def main(args: Array[String]): Unit = {
    val Array(inPath, outPath) = args
    val lines = Files.readAllLines(Paths.get(inPath), StandardCharsets.UTF_8).asScala
    val out = lines.map {
      raw =>
        val label = raw.trimTrailingPeriod
        val range = PeriodParser(label)
        val id = identifierFromText(PeriodParser.preprocess(label), "Period")
        val rangeJson = range match {
          case Some(r) => s"""{"from": ${quote(r.from.toString)}, "to": ${quote(r.to.toString)}}"""
          case None    => "null"
        }
        s"""{"input": ${quote(raw)}, "label": ${quote(label)}, "id": ${quote(id.sourceIdentifier.value)}, "range": $rangeJson}"""
    }
    Files.write(Paths.get(outPath), out.mkString("\n").getBytes(StandardCharsets.UTF_8))
    System.err.println(s"parsed ${out.size} labels")
  }
}
