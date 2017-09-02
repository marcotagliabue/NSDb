package io.radicalbit.nsdb.common.statement

sealed trait CommandStatement extends NSDBStatement

/**
  * CLI command to retrieve the namespaces present in the database.
  */
object ShowNamespaces extends CommandStatement

/**
  * CLI command to use the given namespace.
  */
case class UseNamespace(namespace: String) extends CommandStatement

/**
  * CLI command to retrieve the metrics present in the database for the given namespace.
  */
case class ShowMetrics(namespace: String) extends CommandStatement

/**
  * CLI command to describe the given metric for the give namespace.
  */
case class DescribeMetric(namespace: String, metric: String) extends CommandStatement
