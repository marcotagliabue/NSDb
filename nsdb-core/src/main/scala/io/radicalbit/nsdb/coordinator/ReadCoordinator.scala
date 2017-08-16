package io.radicalbit.nsdb.coordinator

import akka.actor.{Actor, ActorRef, Props}
import io.radicalbit.nsdb.actors.SchemaSupport
import io.radicalbit.nsdb.index.Schema
import io.radicalbit.nsdb.statement.SelectSQLStatement

class ReadCoordinator(val basePath: String, indexerActor: ActorRef) extends Actor with SchemaSupport {
  import ReadCoordinator._

  override def receive: Receive = {
    case ExecuteStatement(statement) =>
      schemaIndex.getSchema(statement.metric) match {
        case Some(schema) => indexerActor.forward(ExecuteSelectStatement(statement, schema))
        case None         => SelectStatementFailed(s"No schema found for metric ${statement.metric}")
      }
  }
}

object ReadCoordinator {

  def props(basePath: String, indexerActor: ActorRef): Props =
    Props(new ReadCoordinator(basePath, indexerActor))

  case class ExecuteStatement(selectStatement: SelectSQLStatement)
  case class ExecuteSelectStatement(selectStatement: SelectSQLStatement, schema: Schema)
  case class SelectStatementExecuted[T](values: Seq[T])
  case class SelectStatementFailed(reason: String)
}
