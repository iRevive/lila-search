package lila.search
package ingestor

import cats.effect.*
import com.sksamuel.elastic4s.Indexable
import lila.search.spec.{ ForumSource, Source }
import mongo4cats.database.MongoDatabase
import smithy4s.schema.Schema

import java.time.Instant

trait Ingestor:
  def run(): IO[Unit]

object Ingestor:

  def apply(mongo: MongoDatabase[IO], elastic: ESClient[IO]): IO[Ingestor] =
    ForumWatch(mongo).map(apply(_, elastic))

  def apply(forum: ForumWatch, elastic: ESClient[IO]): Ingestor = new:
    def run() =
      forum
        .watch(Instant.now())
        .map(_.map(x => x.id -> x.source))
        .evalMap(sources => elastic.storeBulk(???, sources))
        .compile
        .drain

  import smithy4s.json.Json.given
  import com.github.plokhotnyuk.jsoniter_scala.core.*

  given [A: Schema]: Indexable[A] = (a: A) => writeToString(a)
  given Indexable[Source] =
    _ match
      case f: Source.ForumCase => writeToString(f.forum)
      case g: Source.GameCase  => writeToString(g.game)
      case s: Source.StudyCase => writeToString(s.study)
      case t: Source.TeamCase  => writeToString(t.team)
