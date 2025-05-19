import sbt.*

object AppDependencies {
  private val playVersion  = "play-30"
  private val mongoVersion = "2.6.0"
  private val pekkoVersion = "1.1.3"

  private val bootstrapVersion = "9.12.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion"         % bootstrapVersion,
    "org.typelevel"                           %% "cats-core"                               % "2.13.0",
   // "org.julienrf"                            %% "play-json-derived-codecs"                % "11.0.0"  cross CrossVersion.for3Use2_13,
    "com.github.pureconfig"                   %% "pureconfig-generic-scala3"               % "0.17.9",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer"               % "20240325.1",
    "org.apache.pekko"                        %% "pekko-actor-typed"                       % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-protobuf-v3"                       % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-serialization-jackson"             % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-stream"                            % pekkoVersion,
    "uk.gov.hmrc.mongo"                       %% s"hmrc-mongo-work-item-repo-$playVersion" % mongoVersion,
    "uk.gov.hmrc"                             %% "tax-year"                                % "6.0.0",
    "com.openhtmltopdf"                       %   "openhtmltopdf-pdfbox"                   % "1.0.10"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% s"bootstrap-test-$playVersion"  % bootstrapVersion % scope,
    "uk.gov.hmrc.mongo"          %% s"hmrc-mongo-test-$playVersion" % mongoVersion     % scope,
   // "org.mockito"                %% "mockito-scala"                 % "1.17.37"        % scope,
    "org.scalamock"              %% "scalamock"                     % "7.3.2"          % scope,
    //"com.github.alexarchambault" %% "scalacheck-shapeless_1.18"     % "1.3.2"          % scope,
    "org.jsoup"                   % "jsoup"                         % "1.20.1",
    "org.scalatestplus"          %% "scalacheck-1-18"               % "3.2.19.0"       % scope,
    "org.scalatest"              %% "scalatest"                     % "3.2.19"           % scope,
    "org.apache.pekko"           %% "pekko-testkit"                 % pekkoVersion     % scope,
    "io.github.martinhh"         %% "scalacheck-derived"            % "0.8.2"          % scope,
    "org.scalacheck"             %% "scalacheck"                    % "1.18.1"        % scope,
    "io.github.martinhh"         %% "scalacheck-derived" % "0.8.2"  % scope,
    "uk.gov.hmrc"                %% s"bootstrap-test-$playVersion"  % bootstrapVersion  % scope exclude ("org.playframework", "play-json_2.13")
    //"org.julienrf"               %% "play-json-derived-codecs"      % "7.0.0" cross CrossVersion.for3Use2_13
  )
}
