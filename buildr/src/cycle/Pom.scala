package cycle

import mill.scalalib.publish._

object Pom {

  def releaseTag(): Option[String] = {
    val release = os.proc("git", "tag", "-l", "-n0", "--points-at", "HEAD").call().out.trim()
    Option.when(!release.isBlank)(release)
  }

  def commitSha(): String = {
    os.proc("git", "rev-parse", "--short", "HEAD").call().out.trim()
  }

  lazy val publishVersion: String =
    releaseTag().getOrElse(commitSha())

  val pomSettings: PomSettings =
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

}
