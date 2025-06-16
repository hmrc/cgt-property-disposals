import uk.gov.hmrc.DefaultBuildSettings.addTestReportOption

val appName = "cgt-property-disposals"

lazy val ItTest = config("it") extend Test

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    scalaVersion := "3.6.4")
  .settings(
    majorVersion := 2,
    PlayKeys.playDefaultPort := 7021,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    onLoadMessage := "",
    scalafmtOnCompile := true,
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=unused import&src=txt/.*:s",
      "-source:3.5"
    ),
    scalacOptions ~= { options =>
      options.distinct
    }

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
  //Integration test settings
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testSettings) *)
  .settings(
    ItTest / unmanagedSourceDirectories := Seq((ItTest / baseDirectory).value / "it"),
    ItTest / unmanagedClasspath += baseDirectory.value / "resources",
    Runtime / unmanagedClasspath += baseDirectory.value / "resources",
    ItTest / javaOptions += "-Dlogger.resource=logback-test.xml",
    addTestReportOption(ItTest, directory = "int-test-reports")
  )

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
