import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val akkaVersion = "2.6.14"
  val playVersion = "play-28"
  val mongoVersion = "0.70.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo"                       %% "hmrc-mongo-play-28"        % mongoVersion,
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion" % "5.12.0",
    "uk.gov.hmrc"                             %% "work-item-repo"            % s"8.1.0-$playVersion",
    "org.typelevel"                           %% "cats-core"                 % "2.6.0",
    "org.julienrf"                            %% "play-json-derived-codecs"  % "10.0.2",
    "com.github.kxbmap"                       %% "configs"                   % "0.6.1",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % "20191001.1",
    "com.github.ghik"                          % "silencer-lib"              % "1.7.5" % Provided cross CrossVersion.full
  )

  val test = Seq(
    "org.scalatest"              %% "scalatest"                  % "3.2.9"          % "test",
    "com.typesafe.play"          %% "play-test"                  % current          % "test",
    "org.scalamock"              %% "scalamock"                  % "5.1.0"          % "test",
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"  % "1.2.5"          % "test",
    "org.scalatestplus.play"     %% "scalatestplus-play"         % "5.1.0"          % "test, it",
    "org.scalatestplus"          %% "scalatestplus-scalacheck"   % "3.1.0.0-RC2"    % "test, it",
    "com.vladsch.flexmark"        % "flexmark-all"               % "0.35.10"        % "test, it",
    "uk.gov.hmrc.mongo"          %% "hmrc-mongo-test-play-28"    % mongoVersion     % "test",
    "com.eclipsesource"          %% "play-json-schema-validator" % "0.9.5"          % "test",
    "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion      % Test
  )

}
