package com.serverless.lambda.aws.s3.client

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Client}
import zio.{UIO, ZIO}

trait S3ClientsAws extends S3Clients {
  lazy val client: UIO[S3Client] = UIO.effectTotal(S3Client.builder()
    .region(Region.EU_WEST_1)
    .build())
  lazy val asyncClient: UIO[S3AsyncClient] = UIO.effectTotal(S3AsyncClient.builder()
    .region(Region.EU_WEST_1)
    .build())
}
