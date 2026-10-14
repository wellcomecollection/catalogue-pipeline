package weco.pipeline_storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import io.circe.{Encoder, Json}
import weco.elasticsearch.ElasticClientBuilder
import weco.pipeline_storage.Indexable

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/** Write-throughput check for platform#6714: the real ElasticIndexer and
  * elastic4s client against a target, with the merger's shape of many
  * concurrent single-document bulks.
  *
  * Hosts and keys come from ES_BENCH_SOURCE_HOST, ES_BENCH_SOURCE_KEY,
  * ES_BENCH_TARGET_HOST and ES_BENCH_TARGET_KEY. Run with sbt
  * "pipeline_storage/Test/runMain weco.pipeline_storage.elastic.ServerlessBench
  * --source-index=... --target-index=... --docs=2000 --bulk-size=1
  * --concurrency=50"
  */
object ServerlessBench extends App {
  implicit val ec: ExecutionContext = ExecutionContext.global

  case class RawDoc(id: String, version: Long, json: Json)

  implicit val rawIndexable: Indexable[RawDoc] = new Indexable[RawDoc] {
    def id(doc: RawDoc): String = doc.id
    def version(doc: RawDoc): Long = doc.version
  }
  implicit val rawEncoder: Encoder[RawDoc] = Encoder.instance(_.json)

  private val opts = args.collect {
    case a if a.startsWith("--") && a.contains("=") =>
      val Array(k, v) = a.drop(2).split("=", 2)
      k -> v
  }.toMap

  private def opt(name: String, default: String): String =
    opts.getOrElse(name, default)

  private def env(name: String): String =
    sys.env.getOrElse(name, sys.error(s"$name is not set"))

  val sourceIndex = opt("source-index", "works-denormalised-2026-09-30")
  val targetIndex =
    opt("target-index", "works-denormalised-2026-10-14-serverless")
  val docCount = opt("docs", "2000").toInt
  val bulkSize = opt("bulk-size", "1").toInt
  val concurrency = opt("concurrency", "50").toInt
  val rounds = opt("rounds", "1").toInt
  val label = opt("label", "scala")

  private def client(hostVar: String, keyVar: String): ElasticClient =
    ElasticClientBuilder.create(
      hostname = env(hostVar),
      port = 443,
      protocol = "https",
      encodedApiKey = env(keyVar)
    )

  val sourceClient = client("ES_BENCH_SOURCE_HOST", "ES_BENCH_SOURCE_KEY")
  val targetClient = client("ES_BENCH_TARGET_HOST", "ES_BENCH_TARGET_KEY")

  val version = System.currentTimeMillis()

  val docs: Seq[RawDoc] = Await
    .result(
      sourceClient.execute(search(sourceIndex).matchAllQuery().size(docCount)),
      2.minutes
    )
    .result
    .hits
    .hits
    .toSeq
    .map {
      hit =>
        RawDoc(
          id = s"$label-b$bulkSize-c$concurrency-${hit.id}",
          version = version,
          json =
            io.circe.parser.parse(hit.sourceAsString).fold(throw _, identity)
        )
    }
  require(docs.nonEmpty, s"no documents read from $sourceIndex")

  val indexer = new ElasticIndexer[RawDoc](targetClient, Index(targetIndex))
  Await.result(indexer.init(), 1.minute)

  // Checks the get path the merger uses to read its inputs.
  val retriever =
    new ElasticSourceRetriever[Json](targetClient, Index(targetIndex))

  def percentile(sorted: Seq[Double], fraction: Double): Double =
    if (sorted.isEmpty) 0
    else
      sorted(
        math.min(
          sorted.size - 1,
          math.max(0, math.ceil(fraction * sorted.size).toInt - 1)
        )
      )

  (1 to rounds).foreach {
    round =>
      val bulks = docs.grouped(bulkSize).toSeq
      val started = System.nanoTime()
      // Like MergerSQSLambda.processMessages: Future.sequence over a batch, one bulk per message.
      // Groups run one after another so only `concurrency` bulks are in flight at once.
      val outcomes: Seq[(Double, Boolean)] = Await.result(
        bulks
          .grouped(concurrency)
          .foldLeft(Future.successful(Seq.empty[(Double, Boolean)])) {
            (acc, group) =>
              acc.flatMap {
                done =>
                  Future
                    .sequence(group.map {
                      bulk =>
                        val t0 = System.nanoTime()
                        indexer(bulk).map {
                          result =>
                            ((System.nanoTime() - t0) / 1e6, result.isRight)
                        }
                    })
                    .map(done ++ _)
              }
          },
        30.minutes
      )
      val wall = (System.nanoTime() - started) / 1e9
      val latencies = outcomes.map(_._1).sorted
      val failed = outcomes.count(!_._2)
      val written = docs.size - failed * bulkSize
      println(
        s"""{"label":"$label","round":$round,"bulk_size":$bulkSize,"concurrency":$concurrency,""" +
          s""""docs":$written,"wall_seconds":${"%.2f".format(
              wall
            )},"docs_per_second":${"%.1f"
              .format(written / wall)},""" +
          s""""p50_ms":${"%.1f".format(
              percentile(latencies, 0.5)
            )},"p95_ms":${"%.1f"
              .format(percentile(latencies, 0.95))},"p99_ms":${"%.1f"
              .format(percentile(latencies, 0.99))},"failed_bulks":$failed}"""
      )
  }

  val sample = docs.take(10).map(_.id)
  val fetched = Await.result(retriever(sample), 1.minute)
  println(
    s"""{"label":"$label","retriever_requested":${sample.size},"retriever_found":${fetched.found.size},"retriever_not_found":${fetched.notFound.size}}"""
  )

  sourceClient.close()
  targetClient.close()
  sys.exit(0)
}
