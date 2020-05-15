import mill._, scalajslib._, scalalib._, publish._

trait BaseModule extends ScalaJSModule {
  override def scalaVersion   = "2.12.11"
  override def scalaJSVersion = "1.0.1"

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.raquo::laminar::0.9.0"
  )
}

object cycle extends BaseModule with PublishModule {
  def artifactName   = "laminar-cycle"
  def publishVersion = os.read(os.pwd / "VERSION").trim
  def pomSettings = PomSettings(
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

object examples extends Module {

  trait Example extends BaseModule {
    override def moduleDeps = Seq(cycle)
  }

  object cycle_counter extends Example
  object swapi_driver extends Example

}

object pages extends Module {}
