// -*- scala -*-
import ammonite.ops._
import mill._
import mill.define.{Cross, Ctx, Sources, Target}
import mill.scalajslib._
import mill.scalalib._
import mill.scalalib.publish._
import zio.Task

import scala.util.Properties

abstract class CrossO[T](implicit ci: Cross.Factory[T], ctx: Ctx)
    extends Cross[T](meta.crossVersions: _*)

object meta {
  val crossVersions: Seq[(String, String)] = for {
    scala   <- Seq("2.13.3", "2.12.12")
    scalajs <- Seq("1.1.0")
  } yield (scala, scalajs)

  val laminarVersion = "0.9.1"
  val zioVersion     = "1.0.4"

  val publishFullSjs = Properties.propIsSetTo("publish-full-sjs", "true")

  val publishVersion = {
    implicit val wd: os.Path = os.pwd
    val short                = %%("git", "rev-parse", "--short", "HEAD").out.trim
    val release              = %%("git", "tag", "-l", "-n0", "--points-at", "HEAD").out.trim
    release match {
      case "" => short
      case _  => release
    }
  }

  val pomSettings =
    PomSettings(
      description = "cycle.js style user-computer interaction in Laminar",
      organization = "com.github.vic.laminar_cycle",
      url = "https://github.com/vic/laminar_cycle",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("vic", "laminar_cycle"),
      developers = Seq(
        Developer("vic", "Victor Borja", "https://github.com/vic")
      )
    )

  object deps {
    val laminar    = ivy"com.raquo::laminar::${laminarVersion}"
    val zio        = ivy"dev.zio::zio::${zioVersion}"
    val zioStreams = ivy"dev.zio::zio-streams::${zioVersion}"
    val javaTime   = ivy"io.github.cquiroz::scala-java-time::2.0.0"
    val urlDsl     = ivy"be.doeraene::url-dsl::0.2.0"
    val izumiBio   = ivy"io.7mind.izumi::fundamentals-bio::0.10.10"
  }
}

trait BaseModule extends ScalaJSModule {
  val scalaCross: String
  val scalaJSCross: String

  override def scalaVersion: T[String]   = scalaCross
  override def scalaJSVersion: T[String] = scalaJSCross

  implicit object resolver extends Cross.Resolver[BaseModule] {
    def resolve[V <: BaseModule](c: Cross[V]): V =
      c.get(List(scalaCross, scalaJSCross))
  }

  override def sources: Sources = T.sources {
    millOuterCtx.millSourcePath / "src"
  }

  override def scalacOptions: Target[Seq[String]] =
    super.scalacOptions() ++
      (scalaVersion() match {
        case scalalib.api.Util.PartialVersion("2", "12") =>
          Seq(
            "-Ypartial-unification",
            "-Xsource:2.13"
          )
        case _ => Nil
      }) ++
      Seq(
        // "-Xfatal-warnings",
        "-explaintypes",
        "-language:higherKinds",
        "-language:postfixOps"
      )

  override def scalacPluginIvyDeps =
    super.scalacPluginIvyDeps() ++
      Agg(ivy"org.typelevel::kind-projector:0.10.3")
}

object cycle extends CrossO[cycle]
class cycle(val scalaCross: String, val scalaJSCross: String)
    extends BaseModule
    with PublishModule {

  override def artifactId: T[String] =
    if (meta.publishFullSjs) s"${artifactName()}_sjs${scalaJSVersion()}_${artifactScalaVersion()}"
    else super.artifactId()
  override def artifactName = "cycle-core"
  override def publishVersion        = T { meta.publishVersion }
  override def pomSettings           = T { meta.pomSettings }
  override def ivyDeps =
    super.ivyDeps() ++ Agg(meta.deps.laminar)
}

object drivers extends Module {

  sealed trait Driver extends PublishModule with BaseModule {
    def publishVersion        = T { meta.publishVersion }
    def pomSettings           = T { meta.pomSettings }
    def driverName            = millOuterCtx.millSourcePath.last
    override def artifactId: T[String] =
      if (meta.publishFullSjs) s"${artifactName()}_sjs${scalaJSVersion()}_${artifactScalaVersion()}"
      else super.artifactId()
    override def artifactName = s"${driverName}-driver"
    override def moduleDeps   = super.moduleDeps ++ Seq(cycle())
  }

  object all extends CrossO[all]
  class all(val scalaCross: String, val scalaJSCross: String) extends Driver {
    override def artifactName = "cycle"

    override def moduleDeps =
      super.moduleDeps ++
        drivers.millModuleDirectChildren
          .filter(_ != drivers.all)
          .map { mod => resolver.resolve(mod.asInstanceOf[Cross[BaseModule]]) }
          .asInstanceOf[Seq[Driver]]

    def mdocProperties = T {
      val cp = (
        runClasspath()
      ).map(_.path.toString).mkString(":")
      val jsc = scalacPluginClasspath()
        .map(_.path.toString)
        .find(_.contains("scalajs-compiler"))
        .get
      val mdoc =
        s"""
          |js-classpath=${cp}
          |js-scalac-options=-Xplugin:${jsc}
          |""".stripMargin.trim
      os.makeDir.all(T.ctx().dest)
      os.write(T.ctx().dest / "mdoc.properties", mdoc)
    }
  }

  object fetch                                                  extends CrossO[fetch]
  class fetch(val scalaCross: String, val scalaJSCross: String) extends Driver

  object zio extends CrossO[zio]
  class zio(val scalaCross: String, val scalaJSCross: String) extends Driver {
    override def ivyDeps =
      super.ivyDeps() ++ Seq(meta.deps.zioStreams)
  }

  object topic                                                  extends CrossO[topic]
  class topic(val scalaCross: String, val scalaJSCross: String) extends Driver

  object state                                                  extends CrossO[state]
  class state(val scalaCross: String, val scalaJSCross: String) extends Driver

  object tea                                                  extends CrossO[tea]
  class tea(val scalaCross: String, val scalaJSCross: String) extends Driver

  object history                                                  extends CrossO[history]
  class history(val scalaCross: String, val scalaJSCross: String) extends Driver

  object router extends CrossO[router]
  class router(val scalaCross: String, val scalaJSCross: String)
      extends Driver {
    override def ivyDeps = super.ivyDeps() ++ Seq(
      meta.deps.urlDsl.optional(true)
    )
  }

  object mount                                                  extends CrossO[mount]
  class mount(val scalaCross: String, val scalaJSCross: String) extends Driver

}

object examples extends Module {

  def serve(exampleName: String) = T.command {
    webserver.start
    ()
  }

  sealed trait Example extends BaseModule {
    override def moduleDeps = super.moduleDeps :+ drivers.all()
  }

  object hello extends CrossO[hello]
  class hello(val scalaCross: String, val scalaJSCross: String)
    extends Example

  object onion_state extends CrossO[onion_state]
  class onion_state(val scalaCross: String, val scalaJSCross: String)
      extends Example

  object cycle_counter extends CrossO[cycle_counter]
  class cycle_counter(val scalaCross: String, val scalaJSCross: String)
      extends Example {
    override def finalMainClass: T[String] = T("example.cycle_counter.Main")
  }

  object elm_architecture extends CrossO[elm_architecture]
  class elm_architecture(val scalaCross: String, val scalaJSCross: String)
      extends Example {
    override def ivyDeps = super.ivyDeps() ++ Agg(meta.deps.javaTime)
  }
  object swapi_driver extends CrossO[swapi_driver]
  class swapi_driver(val scalaCross: String, val scalaJSCross: String)
      extends Example

  object zio_clock extends CrossO[zio_clock]
  class zio_clock(val scalaCross: String, val scalaJSCross: String)
      extends Example {
    override def ivyDeps = super.ivyDeps() ++ Agg(meta.deps.javaTime)
  }

  object spa_router extends CrossO[spa_router]
  class spa_router(val scalaCross: String, val scalaJSCross: String)
      extends Example {
    override def ivyDeps = super.ivyDeps() ++ Agg(meta.deps.urlDsl)
  }

}

import $ivy.`org.polynote::uzhttp:0.2.5`
object webserver extends zio.App {
  import scala.util.Try

  import java.net.InetSocketAddress
  import uzhttp.server.Server
  import uzhttp.{Status, Request, Response, RefineOps}
  import zio.ZIO

  val (scalaVersion, sjsVersion) = meta.crossVersions.head

  lazy val start = new Thread(() => main(Array.empty)).start

  def jsPath(req: Request): Option[Path] = Try {
    val path = RelPath(req.uri.getPath.stripPrefix("/"))
    assert(path.segments.size == 2) // module/out.js
    os.pwd / os.RelPath(s"out/examples/${path.segments.head}/$scalaVersion/$sjsVersion/fastOpt/dest/${path.last}")
  }.toOption

  def jsExists(req: Request): Boolean = jsPath(req).exists(_.isFile)

  def exampleExists(req: Request): Boolean = exampleModule(req).isDefined

  def exampleModule(req: Request): Option[String] = Try{
    val paths = req.uri.getPath.stripPrefix("/").stripSuffix("/").split('/')
    assert(paths.size == 1)
    val js = os.pwd / os.RelPath(s"out/examples/${paths.head}/$scalaVersion/$sjsVersion/fastOpt/dest/out.js")
    assert(js.isFile)
    paths.head
  }.toOption

  override def run(args:  List[String]): ZIO[zio.ZEnv, Nothing, zio.ExitCode] =
    Server.builder(new InetSocketAddress("127.0.0.1", 8080))
      .handleSome {
        case req if req.uri.getPath == "/" =>
          val links = examples.millModuleDirectChildren.map { module =>
            val name = module.millModuleBasePath.value.baseName
            s"<li><a href='$name'>$name</a></li>"
          }.mkString("<ul>", "", "</ul>")
          ZIO.succeed(Response.html(s"<html><body><h1>Laminar.cycle Examples</h1>$links</body></html>"))

        case req if jsExists(req) =>
          Response.fromPath(jsPath(req).get.toNIO, req, contentType = "application/javascript").refineHTTP(req)

        case req if exampleExists(req) =>
          ZIO.effect {
            os.read(examples.millModuleBasePath.value / "index.html")
              .replace("EXAMPLE_MODULE", exampleModule(req).get)
          }.fold(
            t => Response.plain(t.getMessage, status = Status(500, "Internal Server Error")),
            Response.html(_)
          )

        case req =>
          ZIO.succeed(Response.plain(req.uri.toString, status = Status(404, "Not Found")))

      }.serve.useForever.orDie
}
