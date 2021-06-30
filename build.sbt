name := "sbt-dao-gen"

organization := "com.github.j5ik2o"
homepage := Some(url("https://github.com/j5ik2o/docker-controller-scala"))
licenses := List("The MIT License" -> url("http://opensource.org/licenses/MIT"))

developers := List(
  Developer(
    id = "j5ik2o",
    name = "Junichi Kato",
    email = "j5ik2o@gmail.com",
    url = url("https://blog.j5ik2o.me")
  )
)
scalacOptions ++= Seq(
  "-unchecked",
  "-feature",
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:_"
)
//  ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
//  semanticdbEnabled := true
//  semanticdbVersion := scalafixSemanticdb.revision
Test / publishArtifact := false
Test / fork := true
Test / parallelExecution := false

enablePlugins(SbtPlugin)

val sbtCrossVersion = pluginCrossBuild / sbtVersion

scalaVersion := (CrossVersion partialVersion sbtCrossVersion.value match {
  case Some((1, _)) => "2.12.4"
  case _            => sys error s"Unhandled sbt version ${sbtCrossVersion.value}"
})

crossSbtVersions := Seq("1.3.13")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Seasar Repository" at "https://maven.seasar.org/maven2/"
)

libraryDependencies ++= Seq(
  "us.fatehi"       % "schemacrawler-api"   % "16.15.1",
  "us.fatehi"       % "schemacrawler-mysql" % "16.15.1",
  "ch.qos.logback"  % "logback-classic"     % "1.2.3",
  "org.slf4j"       % "slf4j-api"           % "1.7.30",
  "org.freemarker"  % "freemarker"          % "2.3.30",
  "org.seasar.util" % "s2util"              % "0.0.1",
  "org.scalatest"  %% "scalatest"           % "3.2.9"   % Test,
  "com.h2database"  % "h2"                  % "1.4.187" % Test
)

scriptedBufferLog := false

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dproject.version=" + version.value)
}

scriptedBufferLog := false

addCommandAlias("lint", ";scalafmtCheck;Test/scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt;scalafix RemoveUnused")
