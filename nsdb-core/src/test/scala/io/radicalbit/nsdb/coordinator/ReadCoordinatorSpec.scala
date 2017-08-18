package io.radicalbit.nsdb.coordinator

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import io.radicalbit.nsdb.actors.IndexerActor.{AddRecords, DeleteMetric}
import io.radicalbit.nsdb.actors.SchemaActor.commands.UpdateSchema
import io.radicalbit.nsdb.actors.{IndexerActor, SchemaActor}
import io.radicalbit.nsdb.coordinator.ReadCoordinator._
import io.radicalbit.nsdb.index.{BIGINT, Schema, VARCHAR}
import io.radicalbit.nsdb.model.{Record, RecordOut, SchemaField}
import io.radicalbit.nsdb.statement._
import org.scalatest._

import scala.concurrent.Await

class ReadCoordinatorSpec
    extends TestKit(ActorSystem("nsdb-test"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  val probe                = TestProbe()
  val probeActor           = probe.ref
  private val basePath     = "target/test_index"
  val schemaActor          = system.actorOf(SchemaActor.props(basePath))
  val indexerActor         = system.actorOf(IndexerActor.props(basePath))
  val readCoordinatorActor = system actorOf ReadCoordinator.props(schemaActor, indexerActor)

  val records: Seq[Record] = Seq(
    Record(2, Map("name"  -> "John", "surname" -> "Doe", "creationDate" -> System.currentTimeMillis()), Map.empty),
    Record(4, Map("name"  -> "John", "surname" -> "Doe", "creationDate" -> System.currentTimeMillis()), Map.empty),
    Record(6, Map("name"  -> "John", "surname" -> "Doe", "creationDate" -> System.currentTimeMillis()), Map.empty),
    Record(8, Map("name"  -> "John", "surname" -> "Doe", "creationDate" -> System.currentTimeMillis()), Map.empty),
    Record(10, Map("name" -> "John", "surname" -> "Doe", "creationDate" -> System.currentTimeMillis()), Map.empty)
  )

  override def beforeAll(): Unit = {
    import scala.concurrent.duration._
    implicit val timeout = Timeout(3 second)
    val schema = Schema(
      "people",
      Seq(SchemaField("name", VARCHAR()), SchemaField("surname", VARCHAR()), SchemaField("creationDate", BIGINT())))
    Await.result(schemaActor ? UpdateSchema("people", schema), 1 seconds)
    indexerActor ! AddRecords("people", records)
  }

  override def afterAll(): Unit = {
    indexerActor ! DeleteMetric("people")
  }

  "A statement parser instance" when {

    "receive a select projecting a wildcard" should {
      "execute it successfully" in {

        probe.send(readCoordinatorActor,
                   ExecuteStatement(
                     SelectSQLStatement(namespace = "registry",
                                        metric = "people",
                                        fields = AllFields,
                                        limit = Some(LimitOperator(5)))
                   ))

        val expected = probe.expectMsgType[SelectStatementExecuted[RecordOut]]

        expected.values.size should be(5)
      }
    }

    "receive a select projecting a list of fields" should {
      "execute it successfully" in {
        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(namespace = "registry",
                               metric = "people",
                               fields = ListFields(List("name", "surname", "creationDate")),
                               limit = Some(LimitOperator(5)))
          )
        )

        val expected = probe.expectMsgType[SelectStatementExecuted[RecordOut]]

        expected.values.size should be(5)
      }
    }

    "receive a select containing a range selection" should {
      "execute it successfully" in {
        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(
              namespace = "registry",
              metric = "people",
              fields = ListFields(List("name")),
              condition = Some(Condition(RangeExpression(dimension = "timestamp", value1 = 2L, value2 = 4L))),
              limit = Some(LimitOperator(4))
            )
          )
        )

        val expected = probe.expectMsgType[SelectStatementExecuted[RecordOut]]

        expected.values.size should be(2)
      }
    }

    "receive a select containing a GTE selection" should {
      "execute it successfully" in {
        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(
              namespace = "registry",
              metric = "people",
              fields = ListFields(List("name")),
              condition = Some(Condition(
                ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 10L))),
              limit = Some(LimitOperator(4))
            ))
        )

        val expected = probe.expectMsgType[SelectStatementExecuted[RecordOut]]

        expected.values.size should be(1)

      }
    }

    "receive a select containing a GT AND a LTE selection" should {
      "execute it successfully" in {
        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(
              namespace = "registry",
              metric = "people",
              fields = ListFields(List("name")),
              condition = Some(Condition(TupledLogicalExpression(
                expression1 =
                  ComparisonExpression(dimension = "timestamp", comparison = GreaterThanOperator, value = 2L),
                operator = AndOperator,
                expression2 =
                  ComparisonExpression(dimension = "timestamp", comparison = LessOrEqualToOperator, value = 4l)
              ))),
              limit = Some(LimitOperator(4))
            )
          )
        )

        val expected = probe.expectMsgType[SelectStatementExecuted[RecordOut]]

        expected.values.size should be(1)
      }
    }

    "receive a select containing a GTE OR a LT selection" should {
      "execute it successfully" in {
        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(
              namespace = "registry",
              metric = "people",
              fields = ListFields(List("name")),
              condition = Some(Condition(UnaryLogicalExpression(
                expression = TupledLogicalExpression(
                  expression1 =
                    ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 2L),
                  operator = OrOperator,
                  expression2 =
                    ComparisonExpression(dimension = "timestamp", comparison = LessThanOperator, value = 4L)
                ),
                operator = NotOperator
              ))),
              limit = Some(LimitOperator(4))
            )
          )
        )

        val expected = probe.expectMsgType[SelectStatementExecuted[RecordOut]]

        expected.values.size should be(1)
      }
    }

    "receive a select containing for a non existing entity" should {
      "return an error messge properly" in {
        probe.send(readCoordinatorActor,
                   ExecuteStatement(
                     SelectSQLStatement(namespace = "registry",
                                        metric = "nonexisting",
                                        fields = AllFields,
                                        limit = Some(LimitOperator(5)))
                   ))

        probe.expectMsgType[SelectStatementFailed]
      }
    }
  }
}