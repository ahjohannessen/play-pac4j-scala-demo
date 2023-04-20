import play.sbt.PlayImport
name := "play-pac4j-scala-demo"

version := "12.0.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.10"

val playPac4jVersion = "12.0.0-SNAPSHOT"
val pac4jVersion     = "6.0.0-RC6"

libraryDependencies ++= Seq(
  guice,
  filters,
  "org.pac4j"       %% "play-pac4j"      % playPac4jVersion,
  ("org.pac4j"       % "pac4j-http"      % pac4jVersion).excludeAll(ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "de.svenkubiak"    % "jBCrypt"         % "0.4.3",
  "org.apache.shiro" % "shiro-core"      % "1.11.0",
  "ch.qos.logback"   % "logback-classic" % "1.4.7"
)

resolvers ++= Seq(
  "Sonatype snapshots repository" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

routesGenerator := InjectedRoutesGenerator

run / fork := true

ThisBuild / evictionErrorLevel := Level.Info
