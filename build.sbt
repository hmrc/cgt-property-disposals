val appName = "cgt-property-disposals"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(scalaVersion := "2.13.11")
  .settings(
    majorVersion := 2,
    PlayKeys.playDefaultPort := 7021,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test() ++ Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.13" cross CrossVersion.full)
    ),
    scalafmtOnCompile := true,
    scalacOptions ++= List(
      "-P:silencer:pathFilters=routes"
    )
  )
  .settings(CodeCoverageSettings.settings *)

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
