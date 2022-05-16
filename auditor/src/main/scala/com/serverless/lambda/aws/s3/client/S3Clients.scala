package com.serverless.lambda.aws.s3.client

import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Client}
import zio.UIO

trait S3Clients {
  def client: UIO[S3Client]

  def asyncClient: UIO[S3AsyncClient]
}
