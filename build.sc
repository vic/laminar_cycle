import mill._, scalajslib._, scalalib._, publish._

trait BaseModule extends ScalaJSModule {
  override def scalaVersion   = "2.12.11"
  override def scalaJSVersion = "1.0.1"

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.raquo::laminar::0.9.0"
  )
}

trait BasePublish extends Module {
  def publishVersion = T { os.read(os.pwd / "VERSION").trim }
  def pomSettings = T {
    PomSettings(
      description = "Helpers for Cycle style apps using Laminar",
      organization = "io.github.vic",
      url = "https://github.com/vic/laminar.cycle",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("vic", "laminar.cycle"),
      developers = Seq(
        Developer("vic", "Victor Borja", "https://github.com/vic")
      )
    )
  }
}

trait NeedsCycle extends ScalaJSModule {
  override def moduleDeps = super.moduleDeps :+ cycle
}

object cycle extends BaseModule with BasePublish {
  override def artifactName   = "laminar-cycle"
}

object drivers extends Module {
  trait Driver extends BaseModule with NeedsCycle with BasePublish

  object all extends Driver {
    override def artifactName = "laminar-cycle-all-drivers"
    override def moduleDeps = super.moduleDeps ++ Seq(fetch)
  }

  object fetch extends Driver {
    override def artifactName = "laminar-cycle-fetch-driver"
  }
}

object examples extends Module {

  trait Example extends BaseModule with NeedsCycle

  object cycle_counter extends Example
  object swapi_driver extends Example

}

