import sbt._
import Keys._

import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

object DistBuild extends Build {


  val basicSettings = Defaults.defaultSettings ++ seq(
    version := "1.0.0-SNAPSHOT",
    homepage := Some(new URL("https://lifecosys.com/developer/lifecosys-toolkit")),
    organization := "com.lifecosys",
    organizationHomepage := Some(new URL("https://lifecosys.com")),
    description := "Lifecosys toolkit system, include toolkit, aggregate different SNS service such as facebook, twitter, sina weibo etc.",
    startYear := Some(2013),
    scalaVersion := "2.9.2",
    sbtPlugin := true,
    publishMavenStyle := false,
    scalaBinaryVersion <<= scalaVersion,
    scalacOptions := Seq(
      "-encoding", "utf8",
      "-unchecked",
      "-deprecation"
    )

  )


  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences
  )


  def formattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)


  lazy val sbtDistPluin = Project("sbt-dist-plugin", file("."))
    .settings((basicSettings ++ formatSettings): _*)


}
