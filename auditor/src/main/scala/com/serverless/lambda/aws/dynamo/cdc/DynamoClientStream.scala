package com.serverless.lambda.aws.dynamo.cdc

import DynamoClientStreamLive.StreamCreationError
import com.serverless.lambda.aws.dynamo.client.DynamoClientsAws
import software.amazon.awssdk.services.dynamodb.model.Record
import zio.stream.ZStream
import zio.IO
import zio.clock.Clock

trait DynamoClientStream {
  def getRecordStream: IO[StreamCreationError, ZStream[Clock, Throwable, Record]]
}
object DynamoClientStream {
  import zio._

  val live: ULayer[Has[DynamoClientStream]]
  = (() => new DynamoClientStreamLive with DynamoClientsAws).toLayer

  def getRecordStream: ZIO[Has[DynamoClientStream], StreamCreationError, ZStream[Clock, Throwable, Record]] = {
    ZIO.serviceWith[DynamoClientStream](_.getRecordStream)
  }
}

