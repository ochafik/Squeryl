package org.squeryl.dsl.ast

import org.squeryl.internals._
import org.squeryl.dsl.{QueryYield, AbstractQuery}

class QueryExpressionNode[R](_query: AbstractQuery[R],
                             _queryYield:QueryYield[R],
                             val subQueries: Iterable[QueryableExpressionNode],
                             val views: Iterable[ViewExpressionNode[_]])
  extends QueryExpressionElements
    with QueryableExpressionNode {

  def tableExpressions: Iterable[QueryableExpressionNode] = 
    List(views.filter(v => ! v.isOuterJoined && ! v.inhibited),
         subQueries.filter(v => ! v.isOuterJoined && ! v.inhibited)).flatten

  def outerJoinExpressions: Iterable[OuterJoinExpression] =
    List(views.filter(v => v.isOuterJoined && ! v.inhibited),
         subQueries.filter(v => v.isOuterJoined && ! v.inhibited))
            .flatten.map(v => v.outerJoinExpression.get)
  
  val (whereClause, havingClause, groupByClause, orderByClause) =
     _queryYield.queryElements

  private var _selectList: Iterable[SelectElement] = Iterable.empty

  private var _sample: Option[AnyRef] = None

  def sample:AnyRef = _sample.get

  def owns(aSample: AnyRef) = 
    _sample != None && _sample.get.eq(aSample)
  
  def getOrCreateSelectElement(fmd: FieldMetaData, forScope: QueryExpressionElements) = error("implement me")

  override def toString = {
    val sb = new StringBuffer
    sb.append('QueryExpressionNode + "[")
    if(_query.isRoot)
      sb.append("root:")
    sb.append(id)
    sb.append("]")
    dumpOuterJoinInfoForAst(sb)
    sb.append(":rsm="+_query.resultSetMapper)
    sb.toString
  }

  override def children =
    List(
      selectList.toList,
      views.toList,
      subQueries.toList,
      outerJoinExpressions.map(oje => oje.matchExpression),
      whereClause.toList,
      groupByClause.toList,
      orderByClause.toList      
    ).flatten

  def isChild(q: QueryableExpressionNode):Boolean =
    views.find(n => n == q) != None

  def selectDistinct = _query.selectDistinct

  def isForUpdate = _query.isForUpdate

  def page = _query.page

  def alias = "q" + uniqueId.get

  def getOrCreateAllSelectElements(forScope: QueryExpressionElements): Iterable[SelectElement] = {
    _selectList.map(se => new ExportedSelectElement(se))
  }

  def setOutExpressionNodesAndSample(sl: Iterable[SelectElement], s: AnyRef) = {
    _selectList = sl
    _sample = Some(s)

    if(_query.isRoot) {

      var jdbcIndex = 1
      for(oen <- selectList) {
        oen.prepareMapper(jdbcIndex)
        jdbcIndex += 1
      }

      var idGen = 0
      visitDescendants((node,parent,i) => {
        node.parent = parent

        if(node.isInstanceOf[UniqueIdInAliaseRequired]) {
          val nxn = node.asInstanceOf[UniqueIdInAliaseRequired]
          nxn.uniqueId = Some(idGen)
          idGen += 1
        }
      })
    }
  }

  def selectList: Iterable[SelectElement] = _selectList

  def doWrite(sw: StatementWriter) = {
    val isNotRoot = parent != None

    if(isNotRoot) {
      sw.write("(")
      sw.indent(1)
    }
    sw.databaseAdapter.writeQuery(this, sw)
    if(isNotRoot) {
      sw.unindent(1)
      sw.write(") ")
    }
  }
}
