import play.core.PlayVersion.current
import sbt.*

object AppDependencies {

  val akkaVersion  = "2.6.19"
  val playVersion  = "play-28"
  val mongoVersion = "0.68.0"

  private val bootstrapPlay28Version = "5.25.0"
  private val mockitoCoreVersion     = "2.25.0"
  private val pegdownVersion         = "1.6.0"
  private val hmrcMongoVersion       = "0.68.0"
  private val scalacheckRegexVersion = "0.1.1"
  private val scalacheckVersion      = "1.14.0"
  private val scalaTestPlusVersion   = "5.1.0"
  private val scalaTestVersion       = "3.0.8"
  private val wireMockVersion        = "2.21.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion"         % bootstrapPlay28Version,
    "org.typelevel"                           %% "cats-core"                               % "2.6.0",
    "org.julienrf"                            %% "play-json-derived-codecs"                % "10.0.2",
    "com.github.kxbmap"                       %% "configs"                                 % "0.6.1",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer"               % "20191001.1",
    "uk.gov.hmrc"                             %% "time"                                    % "3.18.0",
    "uk.gov.hmrc.mongo"                       %% s"hmrc-mongo-work-item-repo-$playVersion" % mongoVersion
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "org.scalatest"              %% "scalatest"                  % "3.2.9"       % scope,
    "com.typesafe.play"          %% "play-test"                  % current       % scope,
    "org.scalamock"              %% "scalamock"                  % "5.1.0"       % scope,
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"  % "1.2.5"       % scope,
    "org.scalatestplus.play"     %% "scalatestplus-play"         % "5.1.0"       % scope,
    "org.scalatestplus"          %% "scalatestplus-scalacheck"   % "3.1.0.0-RC2" % scope,
    "com.vladsch.flexmark"        % "flexmark-all"               % "0.35.10"     % scope,
    "uk.gov.hmrc.mongo"          %% "hmrc-mongo-test-play-28"    % mongoVersion  % scope,
    "com.eclipsesource"          %% "play-json-schema-validator" % "0.9.5"       % scope,
    "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion   % scope,
    "com.typesafe.akka"          %% "akka-slf4j"                 % akkaVersion   % scope,
    "com.typesafe.akka"          %% "akka-protobuf-v3"           % akkaVersion   % scope,
    "com.typesafe.akka"          %% "akka-serialization-jackson" % akkaVersion   % scope,
    "com.typesafe.akka"          %% "akka-stream"                % akkaVersion   % scope,
    "com.typesafe.akka"          %% "akka-actor-typed"           % akkaVersion   % scope
  )
}
