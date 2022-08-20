package cycle

object Versions {

  val scala: Seq[String]   = Seq("3.1.1")
  val scalaJS: Seq[String] = Seq("1.10.1")

  val crossScala: Seq[(String, String)] = for {
    sc  <- scala
    sjs <- scalaJS
  } yield (sc, sjs)

  val laminar = "0.14.2"
  val zio     = "2.0.0"

}
