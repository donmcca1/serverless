package com.serverless.lambda.audit

import AuditorLive.AuditingError
import com.serverless.lambda.aws.dynamo.cdc.DynamoClientStream
import com.serverless.lambda.aws.s3.S3
import zio.IO
import zio.clock.Clock

trait Auditor {
  def audit: IO[AuditingError, Unit]
}
object Auditor {
  import zio._
  val live: URLayer[Has[DynamoClientStream] with Has[S3], Has[Auditor]] =
    ((stream: DynamoClientStream, s3: S3) => new AuditorLive(stream, s3)).toLayer

  def audit: ZIO[Has[Auditor], AuditingError, Unit] = {
    ZIO.serviceWith[Auditor](_.audit)
  }
}
