import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.AssemblyPlugin.autoImport.{MergeStrategy, assemblyMergeStrategy}
import sbtassembly.PathList

object Commons {

  val scalaVer = "2.12.3"

  val settings: Seq[Def.Setting[_]] = Seq(
    scalaVersion := scalaVer,
    organization := "io.radicalbit.nsdb",
    resolvers ++= Seq(
      Opts.resolver.mavenLocalFile,
      "Radicalbit Repo" at "https://tools.radicalbit.io/maven/repository/internal/",
      Resolver.bintrayRepo("hseeberger", "maven"),
      "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
    ),
    parallelExecution in Test := false,
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
}
