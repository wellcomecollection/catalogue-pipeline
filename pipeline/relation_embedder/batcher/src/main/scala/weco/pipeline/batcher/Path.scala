package weco.pipeline.batcher
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}

sealed trait Path extends Ordered[Path] {
  val path: String
  override def toString: String = path
  override def compare(that: Path): Int = this.path compare that.path
}

sealed trait PathWithReferent[T] extends Path {
  val referent: T
}

case class PathFromSQS(val path: String, val referent: SQSMessage)
    extends PathWithReferent[SQSMessage]

case class PathFromString(val path: String) extends Path
