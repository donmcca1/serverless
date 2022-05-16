package com.serverless.lambda

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.serverless.lambda.audit.Auditor
import com.serverless.lambda.aws.dynamo.cdc.DynamoClientStream
import com.serverless.lambda.aws.s3.S3
import org.slf4j.LoggerFactory

class LambdaInvocation extends RequestHandler[ScheduledEvent, Unit] {
  val logger = LoggerFactory.getLogger(getClass)

  val appLayer = (DynamoClientStream.live ++ S3.live) >>> Auditor.live

  override def handleRequest(input: ScheduledEvent, context: Context): Unit = {
    logger.info("Auditor function executing...")
    logger.info("Handling event:: {}", input)
    zio.Runtime.default.unsafeRun(Auditor.audit.provideLayer(appLayer))
    logger.info("Auditor function finished executing!!")
  }
}
