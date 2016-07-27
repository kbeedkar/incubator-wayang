package org.qcri.rheem.api

import org.apache.commons.lang3.Validate
import org.qcri.rheem.api
import org.qcri.rheem.basic.data.Record
import org.qcri.rheem.basic.operators.{CollectionSource, TextFileSource}
import org.qcri.rheem.core.api.RheemContext
import org.qcri.rheem.core.plan.rheemplan.{ElementaryOperator, Operator, RheemPlan, UnarySource}
import org.qcri.rheem.core.util.ReflectionUtils
import org.qcri.rheem.jdbc.operators.JdbcTableSource

import scala.collection.JavaConversions
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions
import scala.reflect._

/**
  * Utility to build [[RheemPlan]]s.
  */
class PlanBuilder(rheemContext: RheemContext) {

  private[api] val sinks = ListBuffer[Operator]()

  private[api] val udfJars = scala.collection.mutable.Set[String]()

  // We need to ensure that this module is shipped to Spark etc. in particular because of the Scala-to-Java function wrappers.
  ReflectionUtils.getDeclaringJar(this) match {
    case path: String => udfJars += path
    case _ =>
  }

  def buildAndExecute(jobName: String): Unit = {
    val plan: RheemPlan = new RheemPlan(this.sinks.toArray: _*)
    this.rheemContext.execute(jobName, plan, this.udfJars.toArray:_*)
  }

  def readTextFile(url: String): DataQuanta[String] =
    new TextFileSource(url)

  def loadCollection[T: ClassTag](collection: java.util.Collection[T]): DataQuanta[T] =
    new CollectionSource[T](collection, dataSetType[T])

  def loadCollection[T: ClassTag](iterable: Iterable[T]): DataQuanta[T] =
    loadCollection(JavaConversions.asJavaCollection(iterable))

  def load[T: ClassTag](source: UnarySource[T]): DataQuanta[T] = source

  def customOperator(operator: Operator, inputs: DataQuanta[_]*): IndexedSeq[DataQuanta[_]] = {
    Validate.isTrue(operator.getNumRegularInputs == inputs.size)

    // Set up inputs.
    inputs.zipWithIndex.foreach(zipped => zipped._1.connectTo(operator, zipped._2))

    // Set up outputs.
    for (outputIndex <- 0 until operator.getNumOutputs) yield DataQuanta.create(operator.getOutput(outputIndex))(this)
  }

  implicit private[api] def wrap[T : ClassTag](operator: ElementaryOperator): DataQuanta[T] =
    PlanBuilder.wrap[T](operator)(classTag[T], this)

}

object PlanBuilder {

  implicit private[api] def wrap[T : ClassTag](operator: ElementaryOperator)(implicit planBuilder: PlanBuilder): DataQuanta[T] =
    api.wrap[T](operator)

}
