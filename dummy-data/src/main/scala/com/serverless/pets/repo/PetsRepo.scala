package com.serverless.pets.repo

import com.serverless.lambda.aws.dynamo.client.DynamoClientsAws
import com.serverless.pets.Pet
import com.serverless.pets.repo.PetsRepoLive.PetsRepoError
import zio.IO

trait PetsRepo {
  def put(pet: Pet): IO[PetsRepoError, Unit]
}
object PetsRepo {
  import zio._

  val live: ULayer[Has[PetsRepo]] = (() => new PetsRepoLive with DynamoClientsAws).toLayer

  def put(pet: Pet): ZIO[Has[PetsRepo], PetsRepoError, Unit] = {
    ZIO.serviceWith[PetsRepo](_.put(pet))
  }
}
