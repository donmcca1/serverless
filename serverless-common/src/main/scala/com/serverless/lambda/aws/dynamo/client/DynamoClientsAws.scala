package com.serverless.lambda.aws.dynamo.client

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.streams.{DynamoDbStreamsAsyncClient, DynamoDbStreamsClient}
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}
import zio.UIO

trait DynamoClientsAws extends DynamoClients {
  lazy val client: UIO[DynamoDbClient] = UIO.effectTotal(DynamoDbClient.builder()
    .region(Region.EU_WEST_1)
    .build())
  lazy val asyncClient: UIO[DynamoDbAsyncClient] = UIO.effectTotal(DynamoDbAsyncClient.builder()
    .region(Region.EU_WEST_1)
    .build())
  lazy val streamsClient: UIO[DynamoDbStreamsClient] = UIO.effectTotal(DynamoDbStreamsClient.builder()
    .region(Region.EU_WEST_1)
    .build())
  lazy val asyncStreamsClient: UIO[DynamoDbStreamsAsyncClient] = UIO.effectTotal(DynamoDbStreamsAsyncClient.builder()
    .region(Region.EU_WEST_1)
    .build())
}
