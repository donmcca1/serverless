import sbt._

object Dependencies {
  private lazy val zioVersion = "1.0.14"
  private lazy val log4jVersion = "2.17.1"
  private lazy val awssdkVersion = "2.17.186"
  private lazy val scanamoVersion = "1.0.0-M19"

  lazy val zio = Seq(
    "dev.zio" %% "zio",
    "dev.zio" %% "zio-streams"
  ).map(_ % zioVersion)

  lazy val lambda = Seq(
    "com.amazonaws" % "aws-lambda-java-core"   % "1.2.1",
    "com.amazonaws" % "aws-lambda-java-events" % "3.11.0",
    "com.amazonaws" % "aws-lambda-java-log4j2" % "1.5.1"
  )

  lazy val log4j = Seq(
    "org.apache.logging.log4j" % "log4j-api",
    "org.apache.logging.log4j" % "log4j-core",
    "org.apache.logging.log4j" % "log4j-slf4j-impl"
  ).map(_ % log4jVersion)

  lazy val dynamodb = Seq(
    "software.amazon.awssdk" % "dynamodb"
  ).map(_ % awssdkVersion)

  lazy val s3 = Seq(
    "software.amazon.awssdk" % "s3"
  ).map(_ % awssdkVersion)

  lazy val scanamo = Seq(
    "org.scanamo" %% "scanamo",
    "org.scanamo" %% "scanamo-zio"
  ).map(_ % scanamoVersion)

  lazy val awsRegions = Seq(
    "software.amazon.awssdk" % "regions"
  ).map(_ % awssdkVersion)
}
