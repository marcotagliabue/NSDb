package io.radicalbit.nsdb.index

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{IndexSearcher, Query, Sort}
import org.apache.lucene.store.BaseDirectory

import scala.collection.mutable.ListBuffer
import scala.util.Try

trait Index[RECORDIN, RECORDOUT] {
  def directory: BaseDirectory

  def _keyField: String

  def getWriter = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer))

  def getSearcher = new IndexSearcher(DirectoryReader.open(directory))

  protected def writeRecord(doc: Document, data: RECORDIN): Try[Document]

  protected def write(data: RECORDIN)(implicit writer: IndexWriter): Try[Long]

  def delete(data: RECORDIN)(implicit writer: IndexWriter): Unit

  private def parseQueryResults(searcher: IndexSearcher, query: Query, limit: Int, sort: Option[Sort]) = {
    val docs: ListBuffer[Document] = ListBuffer.empty

    val hits =
      sort.fold(searcher.search(query, limit).scoreDocs)(sort => searcher.search(query, limit, sort).scoreDocs)
    (0 until hits.length).foreach { i =>
      val doc = searcher.doc(hits(i).doc)
      docs += doc
    }
    docs.toList
  }

  def docConversion(document: Document): RECORDOUT

  private[index] def rawQuery(query: Query, limit: Int, sort: Option[Sort]): Seq[Document] = {
    val reader   = DirectoryReader.open(directory)
    val searcher = new IndexSearcher(reader)
    parseQueryResults(searcher, query, limit, sort)
  }

  def query(query: Query, limit: Int, sort: Option[Sort]): Seq[RECORDOUT] = {
    rawQuery(query, limit, sort).map(docConversion)
  }

  def query(field: String, queryString: String, limit: Int, sort: Option[Sort] = None): Seq[RECORDOUT] = {
    val reader   = DirectoryReader.open(directory)
    val searcher = new IndexSearcher(reader)
    val parser   = new QueryParser(field, new StandardAnalyzer())
    val query    = parser.parse(queryString)
    parseQueryResults(searcher, query, limit, sort).map(docConversion)
  }
}
