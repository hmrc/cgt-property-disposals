import sbt.*

object AppDependencies {
  private val playVersion  = "play-30"
  private val mongoVersion = "2.12.0"
  private val pekkoVersion = "1.4.0"

  private val bootstrapVersion = "10.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion"         % bootstrapVersion,
    "org.typelevel"                           %% "cats-core"                               % "2.13.0",
    "com.github.pureconfig"                   %% "pureconfig-generic-scala3"               % "0.17.10",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer"               % "20260102.1",
    "org.apache.pekko"                        %% "pekko-actor-typed"                       % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-protobuf-v3"                       % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-serialization-jackson"             % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-stream"                            % pekkoVersion,
    "uk.gov.hmrc.mongo"                       %% s"hmrc-mongo-$playVersion"                % mongoVersion,
    "io.github.openhtmltopdf"                  % "openhtmltopdf-pdfbox"                    % "1.1.37",
    "uk.gov.hmrc"                             %% "tax-year"                                % "6.0.0"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"          %% s"hmrc-mongo-test-$playVersion" % mongoVersion      % scope,
    "org.apache.pekko"           %% "pekko-testkit"                 % pekkoVersion      % scope,
    "org.scalacheck"             %% "scalacheck"                    % "1.19.0"          % scope,
    "org.scalatestplus"           % "scalacheck-1-18_3"             % "3.2.19.0"        % scope,
    "io.github.martinhh"         %% "scalacheck-derived"            % "0.10.0"          % scope,
    "uk.gov.hmrc"                %% s"bootstrap-test-$playVersion"  % bootstrapVersion  % scope)

}
