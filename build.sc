import mill._, scalajslib._, scalalib._, publish._

object meta {
  val scalaVersion   = "2.12.11"
  val scalaJSVersion = "1.0.1"

  val laminarVersion = "0.9.0"
  val zioVersion     = "1.0.0-RC18-2"

  val publishVersion = os.read(os.pwd / "VERSION").trim
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
}

trait BaseModule extends ScalaJSModule {
  override def scalaVersion   = meta.scalaVersion
  override def scalaJSVersion = meta.scalaJSVersion
}

object cycle extends BaseModule with PublishModule {
  override def artifactName = "cycle"
  def publishVersion        = T { meta.publishVersion }
  def pomSettings           = T { meta.pomSettings }
  override def ivyDeps      = super.ivyDeps() ++ Agg(ivy"com.raquo::laminar::${meta.laminarVersion}")
}

object drivers extends Module {

  trait Driver extends BaseModule with PublishModule {
    def publishVersion      = T { meta.publishVersion }
    def pomSettings         = T { meta.pomSettings }
    override def moduleDeps = super.moduleDeps ++ Seq(cycle)
  }

  object all extends Driver {
    override def artifactName = "all-drivers"
    override def moduleDeps   = super.moduleDeps ++ Seq(
      fetch, zio, topic, combine
    )
  }

  object fetch extends Driver {
    override def artifactName = "fetch-driver"
  }

  object zio extends Driver {
    override def artifactName = "zio-driver"
    override def ivyDeps      = super.ivyDeps() ++ Agg(ivy"dev.zio::zio-streams:${meta.zioVersion}")
  }

  object topic extends Driver {
    override def artifactName = "topic-driver"
  }

  object combine extends Driver {
    override def artifactName = "combine-driver"
  }
}

object examples extends Module {

  trait Example extends BaseModule {
    override def moduleDeps = super.moduleDeps :+ drivers.all
  }

  object cycle_counter extends Example
  object swapi_driver  extends Example

}
