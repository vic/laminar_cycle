package cycle

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import mill.define.{Command, Sources}
import mill.{Module, PathRef, T}
import org.fusesource.scalate.{Binding, TemplateEngine}
import os.{Path, RelPath}
import sttp.tapir.filesServerEndpoints
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class DocsCode(path: Path) {
  def apply(file: String): DocsCode = {
    val p =
      if (file.startsWith("/")) os.pwd / RelPath(file.stripPrefix("/"))
      else path / os.up / RelPath(file)
    new DocsCode(p)
  }

  def lines(from: Int, to: Int): String =
    os.read.lines(path).slice(from, to).mkString("\n")

  def section(name: String, prefix: String = "doc:", map: String => String = identity): String =
    map {
      os.read(path)
        .split(s"$prefix\\+$name\\+|$prefix-$name-", 3)
        .applyOrElse(1, (_: Int) => throw new Exception(s"No section named $name in $path"))
        .strip
    }

  def md(name: String): String =
    section(name, prefix = "md:", map = DocsCode.mdContent(path))

  def code(name: String): String =
    section(name, prefix = "// code:", map = _.split("\n").filterNot(_.contains("// code:")).mkString("\n"))
}

object DocsCode {
  val templateEngine: TemplateEngine = {
    val t = new TemplateEngine()
    t.escapeMarkup = false
    t
  }

  def mdContent(path: Path)(md: String): String = {
    val codeBinding = Binding.of[DocsCode]("source")
    val tmp         = templateEngine.compileSsp(md, Seq(codeBinding))
    templateEngine.layout(
      uri = path.toIO.toURI.toString,
      template = tmp,
      attributes = Map("source" -> new DocsCode(path))
    )
  }

}

trait DocsModule extends Module {

  def mdPaths: Sources = T.sources {
    os.walk(millSourcePath, includeTarget = false, skip = !_.last.endsWith(".md"))
      .map(PathRef(_))
  }

  def nonMdPaths: Sources = T.sources {
    os.walk(millSourcePath, includeTarget = false, skip = _.last.endsWith(".md"))
      .map(PathRef(_))
  }

  def public: T[PathRef] = T {
    nonMdPaths().map(_.path).foreach { path =>
      os.copy(
        path,
        T.dest / path.relativeTo(millSourcePath),
        createFolders = true,
        mergeFolders = true,
        replaceExisting = true
      )
    }

    mdPaths().map(_.path).foreach { mdPath =>
      os.write(
        T.dest / mdPath.relativeTo(millSourcePath),
        DocsCode.mdContent(mdPath)(os.read(mdPath)),
        createFolders = true
      )
    }

    PathRef(T.dest)
  }

  private var started = false
  def serve(): Command[Unit] = T.command {
    val publicPath = public().path
    if (!started) {
      started = true
      startAkka(publicPath)
    }
    ()
  }

  private def startAkka(publicPath: Path): Unit = {
    val cl = this.getClass.getClassLoader
    implicit val system: ActorSystem =
      ActorSystem("docs", ConfigFactory.defaultApplication(cl), cl)
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val filesRoute: Route =
      AkkaHttpServerInterpreter()
        .toRoute(filesServerEndpoints[Future]("docs")(publicPath.toString()))

    val done = Http()
      .newServerAt("localhost", 3000)
      .bind(filesRoute)
      .flatMap(_.whenTerminated)
    pprint.pprintln(s"Serving ${publicPath} now. Please navigate to http://localhost:3000/docs/")

    Runtime.getRuntime.addShutdownHook(new Thread(() => system.terminate()))

    Await.result(done, Duration.Inf)
  }

}
