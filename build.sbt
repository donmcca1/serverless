
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "serverless"
  ).aggregate(auditor, dummyData)

lazy val baseSettings = Seq(
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs@_*) => xs match {
      case _ :+ "Log4j2Plugins.dat" => Log4jMergeStrategy.pluginCache
      case List("services", _*) => MergeStrategy.first
      case _ => MergeStrategy.discard
    }
    case _ => MergeStrategy.first
  }
)

lazy val common = (project in file("serverless-common"))
  .settings(
    name := "common",
    libraryDependencies ++= Dependencies.zio,
    libraryDependencies ++= Dependencies.dynamodb,
    libraryDependencies ++= Dependencies.awsRegions
  )

lazy val auditor = project.settings(
  Seq(
    name := "auditor",
    assembly / assemblyJarName := "bdp-function.jar",
    libraryDependencies ++= Dependencies.zio,
    libraryDependencies ++= Dependencies.log4j,
    libraryDependencies ++= Dependencies.lambda,
    libraryDependencies ++= Dependencies.dynamodb,
    libraryDependencies ++= Dependencies.awsRegions,
    libraryDependencies ++= Dependencies.s3
  ) ++ baseSettings
).dependsOn(common)

lazy val dummyData = (project in file("dummy-data"))
  .settings(
    Seq(
      name := "dummy-data",
      libraryDependencies ++= Dependencies.zio,
      libraryDependencies ++= Dependencies.log4j,
      libraryDependencies ++= Dependencies.lambda,
      libraryDependencies ++= Dependencies.scanamo,
      libraryDependencies ++= Dependencies.dynamodb,
      libraryDependencies ++= Dependencies.awsRegions
    ) ++ baseSettings
  ).dependsOn(common)