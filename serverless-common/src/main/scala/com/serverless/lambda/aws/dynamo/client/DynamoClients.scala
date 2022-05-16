package com.serverless.lambda.aws.dynamo.client

import software.amazon.awssdk.services.dynamodb.streams.{DynamoDbStreamsAsyncClient, DynamoDbStreamsClient}
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}
import zio.UIO

trait DynamoClients {
  def client: UIO[DynamoDbClient]

  def asyncClient: UIO[DynamoDbAsyncClient]

  def streamsClient: UIO[DynamoDbStreamsClient]

  def asyncStreamsClient: UIO[DynamoDbStreamsAsyncClient]
}
