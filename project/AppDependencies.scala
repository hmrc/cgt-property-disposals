import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"       %% "simple-reactivemongo"       % "7.22.0-play-26",
    "uk.gov.hmrc"       %% "bootstrap-play-26"          % "1.3.0",
    "org.typelevel"     %% "cats-core"                  % "2.1.0",
    "org.julienrf"      %% "play-json-derived-codecs"   % "7.0.0",
    "com.github.kxbmap" %% "configs"                    % "0.4.4"
  )

  val test = Seq(
    "org.scalatest"              %% "scalatest"                 % "3.0.8"          % "test",
    "com.typesafe.play"          %% "play-test"                 % current          % "test",
    "org.scalamock"              %% "scalamock"                 % "4.2.0"          % "test",
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.1"          % "test",
    "org.pegdown"                % "pegdown"                    % "1.6.0"          % "test, it",
    "uk.gov.hmrc"                %% "service-integration-test"  % "0.9.0-play-26"  % "test, it",
    "org.scalatestplus.play"     %% "scalatestplus-play"        % "3.1.2"          % "test, it",
    "uk.gov.hmrc"                %% "reactivemongo-test"        % "4.15.0-play-26" % "test",
    "com.eclipsesource"          %% "play-json-schema-validator" % "0.9.5"         % "test"
  )

}
