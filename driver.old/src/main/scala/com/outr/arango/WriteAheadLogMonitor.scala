package com.outr.arango

import com.outr.arango.api.{WALOperation, WALOperations}
import io.youi.util.Time
import reactify.Channel

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class WriteAheadLogMonitor(delay: FiniteDuration, skipHistory: Boolean = true, failureHandler: Throwable => Option[FiniteDuration] = t => {
  scribe.error("Monitor error", t)
  None
}) extends Channel[WALOperation] {
  private var keepAlive = false
  private var last: Option[WALOperations] = None
  private var from: Long = 0L
  private var current: Future[Unit] = Future.successful(())

  val tailed: Channel[WALOperations] = Channel[WALOperations]

  def nextTick: Future[WALOperations] = {
    val promise = Promise[WALOperations]()
    tailed.once(promise.success)
    promise.future
  }

  private[arango] def run(future: Future[WALOperations])(implicit ec: ExecutionContext): Unit = {
    keepAlive = true

    current = future.map(_ => ())
    future.onComplete { complete =>
      val d = complete match {
        case Success(operations) => try {
          if (skipHistory && !operations.fromPresent) {
            // Skip
          } else {
            operations.operations.foreach(static)
          }
          last = Some(operations)
          from = math.max(from, operations.lastIncluded)
          tailed @= operations
          Some(delay)
        } catch {
          case t: Throwable => failureHandler(t)
        }
        case Failure(exception) => failureHandler(exception)
      }
      d match {
        case Some(delay) if keepAlive => last.foreach { ops =>
          current = Time.delay(delay).map(_ => run(ops.tail(from)))
        }
        case _ => // Error or keepAlive caused monitor to stop
      }
    }
  }

  def stop(): Future[Unit] = {
    keepAlive = false
    current
  }
}
