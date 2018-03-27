package io.radicalbit.nsdb.cluster.actor

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import io.radicalbit.nsdb.actors.{ShardAccumulatorActor, ShardKey}
import io.radicalbit.nsdb.cluster.actor.MetricsDataActor._
import io.radicalbit.nsdb.cluster.index.Location
import io.radicalbit.nsdb.common.protocol.Bit
import io.radicalbit.nsdb.common.statement.DeleteSQLStatement
import io.radicalbit.nsdb.index.Schema
import io.radicalbit.nsdb.protocol.MessageProtocol.Commands._
import io.radicalbit.nsdb.protocol.MessageProtocol.Events._

import scala.collection.mutable

/**
  * Actor responsible for dispatching read or write commands to the proper actor and index.
  * @param basePath indexes' root path.
  */
class MetricsDataActor(val basePath: String) extends Actor with ActorLogging {

  lazy val sharding: Boolean = context.system.settings.config.getBoolean("nsdb.sharding.enabled")

  val childActors: mutable.Map[NamespaceKey, ActorRef] = mutable.Map.empty

  private def getChild(db: String, namespace: String): ActorRef =
    childActors.getOrElse(
      NamespaceKey(db, namespace), {
        val child = context.actorOf(ShardAccumulatorActor.props(basePath, db, namespace),
                                    s"shard-accumulator-service-$db-$namespace")
        childActors += (NamespaceKey(db, namespace) -> child)
        child
      }
    )

  implicit val timeout: Timeout = Timeout(
    context.system.settings.config.getDuration("nsdb.namespace-data.timeout", TimeUnit.SECONDS),
    TimeUnit.SECONDS)

  import context.dispatcher

  override def preStart(): Unit = {
    Option(Paths.get(basePath).toFile.list())
      .map(_.toSet)
      .getOrElse(Set.empty)
      .filter(f => Paths.get(basePath, f).toFile.isDirectory)
      .flatMap(db => {
        Paths.get(basePath, db).toFile.list().map(namespace => (db, namespace))
      })
      .foreach {
        case (db, namespace) =>
          childActors += (NamespaceKey(db, namespace) -> context.actorOf(
            ShardAccumulatorActor.props(basePath, db, namespace),
            s"shard-accumulator-service-$db-$namespace"))
      }
  }

  override def receive: Receive = commons orElse shardBehaviour

  def commons: Receive = {
    case GetNamespaces(db) =>
      sender() ! NamespacesGot(db, childActors.keys.filter(_.db == db).map(_.namespace).toSet)
    case msg @ GetMetrics(db, namespace) =>
      getChild(db, namespace) forward msg
    case DeleteNamespace(db, namespace) =>
      val indexToRemove = getChild(db, namespace)
      (indexToRemove ? DeleteAllMetrics(db, namespace))
        .map(_ => {
          indexToRemove ! PoisonPill
          childActors -= NamespaceKey(db, namespace)
          NamespaceDeleted(db, namespace)
        })
        .pipeTo(sender())
    case msg @ DropMetric(db, namespace, _) =>
      getChild(db, namespace).forward(msg)
    case msg @ GetCount(db, namespace, _) =>
      getChild(db, namespace).forward(msg)
    case msg @ ExecuteSelectStatement(statement, _) =>
      getChild(statement.db, statement.namespace).forward(msg)
  }

  def shardBehaviour: Receive = {
    case AddRecordToLocation(db, namespace, bit, location) =>
      getChild(db, namespace).forward(
        AddRecordToShard(db, namespace, ShardKey(location.metric, location.from, location.to), bit))
    case DeleteRecordFromLocation(db, namespace, bit, location) =>
      getChild(db, namespace).forward(
        DeleteRecordFromShard(db, namespace, ShardKey(location.metric, location.from, location.to), bit))
    case ExecuteDeleteStatementInternalInLocations(statement, schema, locations) =>
      getChild(statement.db, statement.namespace).forward(
        ExecuteDeleteStatementInShards(statement, schema, locations.map(l => ShardKey(l.metric, l.from, l.to))))
  }

}

object MetricsDataActor {
  def props(basePath: String): Props = Props(new MetricsDataActor(basePath))

  case class AddRecordToLocation(db: String, namespace: String, bit: Bit, location: Location)
  case class DeleteRecordFromLocation(db: String, namespace: String, bit: Bit, location: Location)
  case class ExecuteDeleteStatementInternalInLocations(statement: DeleteSQLStatement,
                                                       schema: Schema,
                                                       locations: Seq[Location])
}
