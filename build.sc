// -*- scala -*-
import ammonite.ops._
import mill._
import mill.define.{Cross, Sources, Target, Ctx}
import mill.scalajslib._
import mill.scalalib._
import mill.scalalib.publish._

case class CrossV(scalaVersion: String, scalajsVersion: String) {
  def toList: List[String] = List(scalaVersion, scalajsVersion)
}
abstract class CrossO[T](implicit ci: Cross.Factory[T], ctx: Ctx)
    extends Cross[T](meta.crossVersions: _*)
abstract class CrossM(val crossVersion: CrossV) extends Module {
  def this() = this(meta.crossVersions.head)
}

object meta {
  val crossVersions: Seq[CrossV] = for {
    scala   <- Seq("2.13.2", "2.12.11")
    scalajs <- Seq("1.1.0", "1.0.1")
  } yield CrossV(scala, scalajs)

  val laminarVersion = "0.9.1"
  val zioVersion     = "1.0.0-RC19"

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
      description = "Cycle style FRP interfaces using Laminar",
      organization = "com.github.vic.laminar.cycle",
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
  def crossVersion: CrossV

  override def scalaVersion   = crossVersion.scalaVersion
  override def scalaJSVersion = crossVersion.scalajsVersion
  override def scalaJSWorkerVersion =
    "1.0" // TODO: remove when mill#894 is fixed

  implicit object resolver extends mill.define.Cross.Resolver[BaseModule] {
    def resolve[V <: BaseModule](c: Cross[V]): V = c.get(crossVersion.toList)
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
class cycle extends CrossM with BaseModule with PublishModule {
  override def artifactName = "cycle-core"
  def publishVersion        = T { meta.publishVersion }
  def pomSettings           = T { meta.pomSettings }
  override def ivyDeps =
    super.ivyDeps() ++ Agg(meta.deps.laminar)
}

object drivers extends Module {

  sealed trait Driver extends BaseModule with PublishModule {
    def publishVersion        = T { meta.publishVersion }
    def pomSettings           = T { meta.pomSettings }
    override def moduleDeps   = super.moduleDeps ++ Seq(cycle())
    override def artifactName = s"${millModuleBasePath.value.last}-driver"
  }

  object all extends CrossO[all]
  class all extends CrossM with Driver {
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

  object fetch extends CrossO[fetch]
  class fetch  extends CrossM with Driver

  object zio extends CrossO[zio]
  class zio extends CrossM with Driver {
    override def ivyDeps =
      super.ivyDeps() ++ Agg(meta.deps.zioStreams)
  }

  object topic extends CrossO[topic]
  class topic  extends CrossM with Driver

  object state extends CrossO[state]
  class state  extends CrossM with Driver

  object tea extends CrossO[tea]
  class tea  extends CrossM with Driver

  object history extends CrossO[history]
  class history  extends CrossM with Driver

  object mount extends CrossO[mount]
  class mount  extends CrossM with Driver

}

object examples extends Module {

  sealed trait Example extends BaseModule {
    override def moduleDeps = super.moduleDeps :+ drivers.all()
  }

  object onion_state extends CrossO[onion_state]
  class onion_state  extends CrossM with Example

  object cycle_counter extends CrossO[cycle_counter]
  class cycle_counter  extends CrossM with Example

  object elm_architecture extends CrossO[elm_architecture]
  class elm_architecture extends CrossM with Example {
    override def ivyDeps = super.ivyDeps() ++ Agg(meta.deps.javaTime)
  }
  object swapi_driver extends CrossO[swapi_driver]
  class swapi_driver  extends CrossM with Example

  object zio_clock extends CrossO[zio_clock]
  class zio_clock extends CrossM with Example {
    override def ivyDeps = super.ivyDeps() ++ Agg(meta.deps.javaTime)
  }

  object route_history extends CrossO[route_history]
  class route_history extends CrossM with Example {
    override def ivyDeps = super.ivyDeps() ++ Agg(meta.deps.urlDsl)
  }

}
