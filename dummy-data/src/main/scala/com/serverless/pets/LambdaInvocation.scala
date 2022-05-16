package com.serverless.pets

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.serverless.pets.repo.PetsRepo
import org.slf4j.LoggerFactory
import zio.stream.{Sink, ZStream}
import zio.{UIO, ZIO}

class LambdaInvocation extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  val logger = LoggerFactory.getLogger(getClass)

  val appLayer = PetsRepo.live

  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    logger.info("Lambda invocation started.")
    logger.info("Handling event {}.", input)
    zio.Runtime.default.unsafeRun(app.provideLayer(appLayer))
    new APIGatewayProxyResponseEvent()
  }

  val petSpecies = List("Dog", "Cat", "Cougar", "Bear", "Fish", "Turtle", "Hamster", "Giraffe", "Pony", "Donkey",
    "Monkey", "Mouse", "Lion", "Snake")

  val app = for {
    _ <- ZStream.range(1, 10)
      .tap(i => UIO(logger.info(s"Emit $i before broadcasting.")))
      .broadcast(2, 10)
      .use {
        case s1 :: s2 :: Nil =>
          for {
            unit1 <- s1.map(i => Pet(s"$i", petSpecies(i % petSpecies.length))).run(Sink.foreach(PetsRepo.put)).fork
            unit2 <- s2.map(i => Pet(s"${i + 1000}", petSpecies(i * i % petSpecies.length))).run(Sink.foreach(PetsRepo.put)).fork
            _ <- unit1.join <&> unit2.join
          } yield ()
        case _ => ZIO.dieMessage("Unhandled case")
      }
  } yield ()
}
