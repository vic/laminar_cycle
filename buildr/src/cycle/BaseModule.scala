package cycle

import mill.T
import mill.define.{Sources, Target}
import mill.scalalib.PublishModule

trait BaseModule extends CrossScalaModule with PublishModule {

  override def sources: Sources = T.sources(
    millOuterCtx.millSourcePath / "src",
    millOuterCtx.millSourcePath / s"src-${scalaVersion().split('.').head}",
    millOuterCtx.millSourcePath / s"src-sjs-${scalaJSVersion().split('.').head}"
  )

  //  override def artifactId: T[String] =
  //    if (publishFullSjs) s"${artifactName()}_sjs${scalaJSVersion()}_${artifactScalaVersion()}"
  //    else super.artifactId()

  override def artifactName: T[String] = millOuterCtx.millSourcePath
    .segments.dropWhile(s => !os.isDir(os.pwd / s)).mkString("-")

  override def publishVersion = T { Pom.publishVersion }
  override def pomSettings    = T { Pom.pomSettings }

  override def scalacOptions: Target[Seq[String]] =
    super.scalacOptions() ++ Seq(
      // Extra Warnings/Details
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-unchecked",
      "-language:higherKinds",
      "-language:postfixOps",
      "-language:implicitConversions",
      // -X
      "-Xfatal-warnings"
    )

}
