import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
//import wartremover.WartRemover.autoImport.wartremoverExcluded

val appName = "cgt-property-disposals"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("fix", "all compile:scalafix test:scalafix")

//lazy val wartremoverSettings =
//  Seq(
//     Compile / compile / wartremoverErrors ++= Warts.allBut(
//      Wart.DefaultArguments,
//      Wart.ImplicitConversion,
//      Wart.ImplicitParameter,
//      Wart.Nothing,
//      Wart.Overloading,
//      Wart.ToString
//    ),
//    Compile / compile / wartremoverExcluded ++=
//      (Compile / routes).value ++
//        (baseDirectory.value ** "*.sc").get ++
//        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala"),
//    Test / compile / wartremoverErrors --= Seq(Wart.NonUnitStatements, Wart.Null, Wart.PublicInference, Wart.Any)
//  )

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*(config|views.*);.*(BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(scalaVersion := "2.12.16")
  .settings(
    majorVersion := 2,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test ++ Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.9" cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % "1.7.9" % Provided cross CrossVersion.full
    )
  )
  .settings(routesImport := Seq.empty)
  .settings(TwirlKeys.templateImports := Seq.empty)
  .settings(
    scalafmtOnCompile := true,
//    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos",
      "-language:postfixOps"
    ),
    Test / scalacOptions --= Seq("-Ywarn-value-discard")
  )
  .settings(scalacOptions ++= Seq("-Yrangepos", "-Ywarn-unused:imports"))
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(Test / resourceDirectory := baseDirectory.value / "/conf/resources")
//  .settings(wartremoverSettings: _*)
  .settings(scoverageSettings: _*)
  .settings(PlayKeys.playDefaultPort := 7021)

addCompilerPlugin("org.typelevel"  %% "kind-projector"  % "0.13.2" cross CrossVersion.full),
