package com.serverless.pets.repo

import com.serverless.lambda.aws.dynamo.client.DynamoClients
import com.serverless.pets.Pet
import com.serverless.pets.repo.PetsRepoLive.{PetsRepoError, PetsTableDbError, logger, table}
import org.scanamo.generic.auto._
import org.scanamo.{ScanamoZio, Table}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import zio.{IO, UIO}

object PetsRepoLive {
  sealed trait PetsRepoError
  final case class PetsTableDbError(message: String, ex: Throwable) extends PetsRepoError

  lazy val logger = LoggerFactory.getLogger(getClass)

  lazy val client = DynamoDbAsyncClient.builder()
    .region(Region.EU_WEST_1)
    .build()

  lazy val table = Table[Pet]("PetsTable")
}
class PetsRepoLive extends PetsRepo {
  this: DynamoClients =>

  lazy val scanamo = asyncClient.map(ScanamoZio(_))

  override def put(pet: Pet): IO[PetsRepoError, Unit] = {
    for {
      _ <- UIO(logger.info(s"putting pet $pet"))
      scan <- scanamo
      _ <- scan.exec {
        for {
          _ <- table.put(pet)
        } yield ()
      }.mapError(PetsTableDbError(s"Unable to add pet to table: $pet.", _))
    } yield ()
  }
}
