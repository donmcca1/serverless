package com.serverless.lambda.aws.s3

import S3Live.S3Error
import com.serverless.lambda.aws.s3.S3Live.S3Error
import com.serverless.lambda.aws.s3.client.S3ClientsAws
import zio.IO
import zio.stream.ZStream

import java.nio.ByteBuffer

trait S3 {
  def multipartUpload(stream: ZStream[Any, Nothing, ByteBuffer]): IO[S3Error, Unit]
}
object S3 {
  import zio._
  val live: ULayer[Has[S3]] = (() => new S3Live with S3ClientsAws).toLayer

  def multipartUpload(stream: ZStream[Any, Nothing, ByteBuffer]): ZIO[Has[S3],S3Error, Unit] = {
    ZIO.serviceWith[S3](_.multipartUpload(stream))
  }
}
