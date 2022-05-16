package com.serverless.lambda.aws.s3

import com.serverless.lambda.aws.s3.S3Live._
import com.serverless.lambda.aws.s3.client.S3Clients
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._
import zio.stream.{ZStream, ZTransducer}
import zio.{Chunk, IO, Task, UIO}

import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

object S3Live {
  val logger = LoggerFactory.getLogger(getClass)

  val bucketName = Task.effect(System.getenv("CSV_BUCKET"))
    .tap(bucket => UIO(logger.info(s"Using bucket for upload: $bucket")))
    .mapError(BucketNameMissing)

  val bucketKey = Task.effect(System.getenv("CSV_KEY"))
    .map(_ + s"-${ZonedDateTime.now().toString}.csv")
    .tap(key => UIO(logger.info(s"Using key for upload: $key")))
    .mapError(BucketKeyMissing)

  val partSize = Task.effect(System.getenv("S3_MULTIPART_PART_SIZE"))
    .map(_.toInt)
    .tap(part => UIO(logger.info(s"Using part size for upload: $part")))
    .mapError(UploadPartSizeMissing)

  sealed trait S3Error
  final case class BucketNameMissing(ex: Throwable) extends S3Error
  final case class BucketKeyMissing(ex: Throwable) extends S3Error
  final case class UploadPartSizeMissing(ex: Throwable) extends S3Error
  final case class InitMultipartUploadError(ex: Throwable) extends S3Error
  final case class CompleteMultipartUploadError(ex: Throwable) extends S3Error
  final case class MultipartUploadError(ex: Throwable) extends S3Error
  final case class UploadPartError(ex: Throwable) extends S3Error
}
class S3Live extends S3 {
  this: S3Clients =>

  override def multipartUpload(stream: ZStream[Any, Nothing, ByteBuffer]): IO[S3Live.S3Error, Unit] = {
    for {
      initMultiPartUpload <- initMultipartUpload()
      completedParts <- uploadParts(stream, initMultiPartUpload)
      _ <- completeUpload(initMultiPartUpload.uploadId(), completedParts)
    } yield ()
  }

  private def initMultipartUpload(): IO[S3Live.S3Error, CreateMultipartUploadResponse] = {
    for {
      bucket <- bucketName
      key <- bucketKey
      client <- asyncClient
      request <- UIO(CreateMultipartUploadRequest.builder()
        .bucket(bucket)
        .key(key)
        .metadata(Map("filename" -> key).asJava)
        .build())
      response <- Task.fromCompletableFuture(client.createMultipartUpload(request)).mapError(InitMultipartUploadError)
    } yield response
  }

  private def uploadParts[E](stream: ZStream[Any, Nothing, ByteBuffer], response: CreateMultipartUploadResponse): IO[S3Live.S3Error, List[CompletedPart]] = {
    for {
      name <- bucketName
      key <- bucketKey
      client <- asyncClient
      partSizeMb <- partSize
      batchedStream <- createBatchedStream(stream, partSizeMb)
      uploadedPartStream <- createStreamOfUploadedParts(client, batchedStream, name, key, response.uploadId())
      uploadedParts <- uploadedPartStream.fold(List[CompletedPart]())((parts, part) => part :: parts)
    } yield uploadedParts
  }

  private def createBatchedStream(stream: ZStream[Any, Nothing, ByteBuffer], partSizeMb: Int): UIO[ZStream[Any, Nothing, ByteBuffer]] = {
    UIO(stream.aggregate {
      ZTransducer.foldWeighted[ByteBuffer, Chunk[ByteBuffer]](Chunk[ByteBuffer]())(
        (_, buffer) => buffer.capacity(), partSizeMb
      ) {
        (chunk, buffer) => chunk ++ Chunk(buffer)
      }.map(_.foldLeft(ByteBuffer.wrap(Array[Byte]()))((acc, buff) => ByteBuffer.wrap(acc.array() ++ buff.array())))
    })
  }

  private def createStreamOfUploadedParts(client: S3AsyncClient, stream: ZStream[Any, Nothing, ByteBuffer], bucket: String, key: String, uploadId: String): UIO[ZStream[Any, S3Live.S3Error, CompletedPart]] = {
    UIO(stream.zipWithIndex.mapMParUnordered(5) { (bufferWithIndex: (ByteBuffer, Long)) =>
      val uploadPartRequest = UploadPartRequest.builder()
          .bucket(bucket)
          .key(key)
          .partNumber(bufferWithIndex._2.toInt + 1)
          .uploadId(uploadId)
          .contentLength(bufferWithIndex._1.capacity())
          .build()
      val uploadPartFuture = client.uploadPart(uploadPartRequest, AsyncRequestBody.fromByteBuffer(bufferWithIndex._1))
      val completedPartFuture: CompletableFuture[CompletedPart] = uploadPartFuture
        .thenApply(uploadPartResult => CompletedPart.builder()
          .partNumber(bufferWithIndex._2.toInt + 1)
          .eTag(uploadPartResult.eTag())
          .build())
      Task.fromCompletableFuture(completedPartFuture).mapError(UploadPartError)
    })
  }

  private def completeUpload(uploadId: String, completedParts: List[CompletedPart]): IO[S3Live.S3Error, Unit] = {
    for {
      bucket <- bucketName
      key <- bucketKey
      client <- asyncClient
      request <- UIO(CompleteMultipartUploadRequest.builder()
        .bucket(bucket)
        .key(key)
        .uploadId(uploadId)
        .multipartUpload(CompletedMultipartUpload.builder()
          .parts(completedParts.asJava)
          .build())
        .build())
      _ <- Task.fromCompletableFuture(client.completeMultipartUpload(request))
        .mapError(CompleteMultipartUploadError)
    } yield ()
  }
}
