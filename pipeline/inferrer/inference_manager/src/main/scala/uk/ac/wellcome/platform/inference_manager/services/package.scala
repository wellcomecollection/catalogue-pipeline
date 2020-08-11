package uk.ac.wellcome.platform.inference_manager

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, FlowWithContext}
import uk.ac.wellcome.models.work.internal.{Identified, MergedImage, Minted}

import scala.util.Try

package object services {
  type MergedIdentifiedImage = MergedImage[Identified, Minted]

  type RequestPoolFlow[T, Ctx] =
    Flow[(HttpRequest, (T, Ctx)), (Try[HttpResponse], (T, Ctx)), _]

  implicit class RequestPoolFlowOps[T, Ctx](
    requestPool: RequestPoolFlow[T, Ctx]) {
    def asContextFlow
      : FlowWithContext[(HttpRequest, T), Ctx, (Try[HttpResponse], T), Ctx, _] =
      requestPool
        .asFlowWithContext[(HttpRequest, T), Ctx, Ctx] { (input, ctx) =>
          (input, ctx) match {
            case ((req, t), ctx) =>
              (req, (t, ctx))
          }
        } {
          case (_, (_, ctx)) => ctx
        }
        .map[(Try[HttpResponse], T)] {
          case (triedResponse, (t, _)) =>
            (triedResponse, t)
        }
  }
}
