import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "com.github.kxbmap"       %% "configs"                  % "0.4.4",
    "uk.gov.hmrc"             %% "simple-reactivemongo"     % "7.19.0-play-26",
    "uk.gov.hmrc"             %% "bootstrap-play-26"        % "0.42.0",
    "org.typelevel"           %% "cats-core"                % "1.6.1",
    "org.julienrf"            %% "play-json-derived-codecs" % "3.3"
  )

  val test = Seq(
    "org.scalatest"              %% "scalatest"                 % "3.0.8"                 % "test",
    "com.typesafe.play"          %% "play-test"                 % current                 % "test",
    "org.scalamock"              %% "scalamock"                 % "4.2.0"                 % "test",
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.1"                 % "test",
    "org.pegdown"                %  "pegdown"                   % "1.6.0"                 % "test, it",
    "uk.gov.hmrc"                %% "service-integration-test"  % "0.9.0-play-26"         % "test, it",
    "org.scalatestplus.play"     %% "scalatestplus-play"        % "3.1.2"                 % "test, it"
  )

}
