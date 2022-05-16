package com.serverless

package object pets {
  sealed trait Operation
  case object Create extends Operation
  case object Update extends Operation
  case object Delete extends Operation

  case class Pet(name: String, species: String)
  case class PetOperation(pet: Pet, operation: Operation)
}
