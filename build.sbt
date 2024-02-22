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
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    onLoadMessage := "",
    scalafmtOnCompile := true,
    scalacOptions ++= Seq("-Wconf:src=routes/.*:s", "-Xlint:-byname-implicit")
  )
  .settings(CodeCoverageSettings.settings *)
  // Disable default sbt Test options (might change with new versions of bootstrap)
  .settings(
    Test / testOptions -= Tests.Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report")
  )
  // Suppress successful events in Scalatest in standard output (-o)
  // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
  .settings(
    Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"
    )
  )

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
