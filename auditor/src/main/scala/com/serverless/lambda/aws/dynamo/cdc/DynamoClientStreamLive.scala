package com.serverless.lambda.aws.dynamo.cdc

import com.serverless.lambda.aws.dynamo.cdc.DynamoClientStreamLive.{StreamAccessError, StreamTableAccessError}
import DynamoClientStreamLive._
import com.serverless.lambda.aws.dynamo.client.DynamoClients
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient
import zio.clock.Clock
import zio.stream.ZStream
import zio.{IO, Task, UIO, duration}

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

object DynamoClientStreamLive {
  sealed trait StreamCreationError
  final case class StreamInitError(ex: Throwable) extends StreamCreationError
  final case class StreamTableAccessError(ex: Throwable) extends StreamCreationError
  final case class StreamAccessError(ex: Throwable) extends StreamCreationError
  final case class MissingTableNameError(ex: Throwable) extends StreamCreationError
  final case class MissingShardKeepAliveError(ex: Throwable) extends StreamCreationError
  final case class MissingShardPollTimeError(ex: Throwable) extends StreamCreationError

  sealed trait RecordStreamError
  final case class RecordFetchError(ex: Throwable) extends RecordStreamError

  val logger = LoggerFactory.getLogger(getClass)
  val tableName = Task.effect(System.getenv("TABLE_NAME"))
    .tap(table => UIO(logger.info(s"Using table to create audit: $table")))
    .mapError(MissingTableNameError)
  val shardIteratorKeepAliveTimeSeconds: IO[StreamCreationError, Int] = Task.effect(System.getenv("SHARD_KEEP_ALIVE_SECONDS"))
    .map(_.toInt)
    .tap(shardKeepAliveTime => UIO(logger.info(s"Using shard keep alive : $shardKeepAliveTime")))
    .mapError(MissingShardKeepAliveError)
  val shardIteratorPollTimeSeconds: IO[StreamCreationError, Int] = Task.effect(System.getenv("SHARD_POLL_SECONDS"))
    .map(_.toInt)
    .tap(shardPollTime => UIO(logger.info(s"Using shard keep alive : $shardPollTime")))
    .mapError(MissingShardPollTimeError)
}
class DynamoClientStreamLive extends DynamoClientStream {
  this: DynamoClients =>

  def getRecordStream: IO[StreamCreationError, ZStream[Clock, Throwable, Record]] = {
    for {
      table <- tableName
      client <- asyncClient
      streamsClient <- asyncStreamsClient
      _ <- UIO(logger.info(s"Creating client stream for table: $table."))
      tableDescription <- describeTable(table, client)
      _ <- UIO(logger.info(s"Fetched table description for $table. Description={$tableDescription}"))
      streamArn <- UIO.effectTotal(tableDescription.table().latestStreamArn())
      streamDescription <- describeStream(streamArn, streamsClient)
      _ <- UIO(logger.info(s"Fetched stream description for $streamArn. Description={$streamDescription}"))
      stream <- getRecordStream(streamDescription, streamArn)
    } yield stream
  }

  private def describeTable(table: String, client: DynamoDbAsyncClient): IO[StreamTableAccessError, DescribeTableResponse] = {
    Task.fromCompletableFuture(client.describeTable(DescribeTableRequest.builder().tableName(table).build()))
      .mapError(StreamTableAccessError)
  }

  private def describeStream(streamArn: String, client: DynamoDbStreamsAsyncClient): IO[StreamAccessError, DescribeStreamResponse] = {
    Task.fromCompletableFuture(client.describeStream(DescribeStreamRequest.builder().streamArn(streamArn).build()))
      .mapError(StreamAccessError)
  }

  private def getRecordStream(streamDescription: DescribeStreamResponse, streamArn: String) = {
    for {
      client <- asyncStreamsClient
      shardKeepAliveTime <- shardIteratorKeepAliveTimeSeconds
      stream <- UIO.effectTotal {
        ZStream.fromJavaIterator(streamDescription.streamDescription().shards().iterator())
          .mapMParUnordered(5)(shard => Task.fromCompletableFuture {
            val request = GetShardIteratorRequest.builder()
              .streamArn(streamArn)
              .shardId(shard.shardId())
              .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
              .build()
            val result = client.getShardIterator(request)
            result.thenApply(res => res.shardIterator()): CompletableFuture[String]
          }).flatMapParSwitch(5, 64)(iterator => {
            ZStream.paginateM(iterator)(it =>
              Task.fromCompletableFuture(client.getRecords(GetRecordsRequest.builder()
                .shardIterator(it).build()))
                .map(result => result.records() -> Option[String](result.nextShardIterator()))
            ).mapConcat(_.asScala).timeout(duration.Duration.fromScala(shardKeepAliveTime.seconds))
        })
      }
    } yield stream
  }
}
