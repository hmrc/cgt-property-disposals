import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val akkaVersion = "2.5.23"

  val compile = Seq(
    "uk.gov.hmrc"                             %% "simple-reactivemongo"      % "8.0.0-play-26",
    "uk.gov.hmrc"                             %% "bootstrap-backend-play-26" % "5.0.0",
    "uk.gov.hmrc"                             %% "work-item-repo"            % "7.10.0-play-26",
    "org.typelevel"                           %% "cats-core"                 % "2.6.0",
    "org.julienrf"                            %% "play-json-derived-codecs"  % "9.0.0",
    "com.github.kxbmap"                       %% "configs"                   % "0.4.4",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % "20191001.1",
    "com.github.ghik"                          % "silencer-lib"              % "1.7.1" % Provided cross CrossVersion.full
  )

  val test = Seq(
    "org.scalatest"              %% "scalatest"                  % "3.0.9"          % "test",
    "com.typesafe.play"          %% "play-test"                  % current          % "test",
    "org.scalamock"              %% "scalamock"                  % "4.2.0"          % "test",
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"  % "1.2.1"          % "test",
    "org.pegdown"                 % "pegdown"                    % "1.6.0"          % "test, it",
    "org.scalatestplus.play"     %% "scalatestplus-play"         % "3.1.2"          % "test, it",
    "uk.gov.hmrc"                %% "reactivemongo-test"         % "4.21.0-play-26" % "test",
    "com.eclipsesource"          %% "play-json-schema-validator" % "0.9.5"          % "test",
    "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion      % Test
  )

}
