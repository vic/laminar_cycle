import os._
import mill._
import mill.scalalib._

import coursier.MavenRepository

object buildr extends ScalaModule {

  override def millSourcePath: Path = super.millSourcePath / os.up

  override def scalaVersion = BuildInfo.scalaVersion

  override def scalacOptions: T[Seq[String]] = super.scalacOptions() ++ Seq(
    // Extra Warnings/Details
    "-deprecation",
    "-explaintypes",
    "-feature",
    "-unchecked",
    // -W
    "-Wdead-code",
    "-Wnumeric-widen",
    "-Wunused:imports",
    // -X
    "-Xfatal-warnings",
    "-Xlint:missing-interpolator"
  )

  override def ivyDeps =
    super.ivyDeps() ++
      BuildInfo.millEmbeddedDeps.map(Dep.parse) ++
      Agg(
        ivy"com.lihaoyi::mill-contrib-bloop:${BuildInfo.millVersion}",
        ivy"com.lihaoyi::mill-contrib-scoverage:${BuildInfo.millVersion}",
        ivy"org.scalatra.scalate::scalate-core:1.9.8",
        ivy"com.softwaremill.sttp.tapir::tapir-akka-http-server:1.0.5"
      )

  override def repositoriesTask = T.task {
    Seq(
      MavenRepository("https://maven-central.storage.googleapis.com/"),
      MavenRepository("https://maven-central.storage-download.googleapis.com/maven2/"),
      MavenRepository("https://jitpack.io"),
    ) ++ super.repositoriesTask()
  }

  def logger = {
    val colors = interp.colors()
    new mill.util.PrintLogger(
      colors != ammonite.util.Colors.BlackWhite,
      false,
      colors.info(),
      colors.error(),
      System.out,
      System.err,
      System.err,
      System.in,
      debugEnabled = true,
      context = ""
    )
  }

  def evaluator = {
    mill.eval.Evaluator(
      home,
      pwd / "out" / "buildr-eval",
      pwd / "out" / "buildr-eval",
      rootModule = Buildr.millSelf.get,
      baseLogger = logger
    )
  }

  def loadBuildrRuntime: Unit = {
    val eval  = mill.eval.Evaluator.evalOrThrow(evaluator)
    val paths = eval[Seq[PathRef]](buildr.runClasspath)
    interp.load.cp(paths.map(_.path))
  }

}

buildr.loadBuildrRuntime
