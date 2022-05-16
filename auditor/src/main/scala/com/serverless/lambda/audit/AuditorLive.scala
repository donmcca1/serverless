package com.serverless.lambda.audit

import AuditorLive._
import com.serverless.lambda.aws.dynamo.cdc.DynamoClientStream
import com.serverless.lambda.aws.s3.S3
import com.serverless.lambda.aws.dynamo.cdc.DynamoClientStreamLive._
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.model.{OperationType, Record}
import zio.clock.Clock
import zio.stream.{Transducer, ZTransducer}
import zio.{IO, UIO}

import java.nio.ByteBuffer
import java.util.stream.Collectors

object AuditorLive {
  sealed trait AuditingError
  case object UploadError extends AuditingError
  final case class DbStreamCreationError(ex: Throwable) extends AuditingError
}
class AuditorLive(stream: DynamoClientStream, s3: S3) extends Auditor {
  val logger = LoggerFactory.getLogger(getClass)

  def audit: IO[AuditingError, Unit] = {
    for {
      _ <- UIO(logger.info("Starting audit."))
      stream <- stream.getRecordStream.mapError(launderStreamCreationError)
      recordToCSVFlow <- createRecordToCsvFlow
      _ <- s3.multipartUpload(stream.transduce(recordToCSVFlow)
        .intersperse(ByteBuffer.wrap("\n".getBytes)).either
        .collectRight
        .provideLayer(Clock.live))
        .mapError(_ => UploadError)
    } yield ()
  }

  def createRecordToCsvFlow: UIO[Transducer[AuditingError, Record, ByteBuffer]] = {
    UIO.effectTotal(ZTransducer.fromFunction[Record, String](record => {
      record.eventName() match {
        case OperationType.INSERT => OperationType.INSERT.toString + "," + record.dynamodb().newImage().values().stream().map(_.toString).collect(Collectors.joining(","))
        case OperationType.MODIFY => OperationType.MODIFY.toString + "," + record.dynamodb().newImage().values().stream().map(_.toString).collect(Collectors.joining(","))
        case OperationType.REMOVE => OperationType.REMOVE.toString + "," + record.dynamodb().oldImage().values().stream().map(_.toString).collect(Collectors.joining(","))
        case _ => "ERROR"
      }
    }).map(_.getBytes).map(arr => ByteBuffer.wrap(arr)))
  }

  def launderStreamCreationError(error: StreamCreationError): AuditingError = {
    error match {
      case StreamInitError(ex) => DbStreamCreationError(ex)
      case StreamAccessError(ex) => DbStreamCreationError(ex)
      case StreamTableAccessError(ex) => DbStreamCreationError(ex)
    }
  }
}
