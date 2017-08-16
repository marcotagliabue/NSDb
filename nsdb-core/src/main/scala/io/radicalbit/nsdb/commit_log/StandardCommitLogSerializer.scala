package io.radicalbit.nsdb.commit_log

import java.nio.ByteBuffer

/**
  * Utility class to Serialize and Deserialize a CommitLogEntry.
  * Please note that this class is not intended to be thread safe.
  */
class StandardCommitLogSerializer extends CommitLogSerializer {

  // TODO: allocate the ByteBuffer reading the size from the configuration
  private val readByteBuffer = new ReadBuffer(5000)
  private val writeBuffer    = new WriteBuffer(5000)

  override def deserialize(entry: Array[Byte]): InsertNewEntry = {
    readByteBuffer.clear(entry)

    // timestamp
    val ts = readByteBuffer.read.toLong
    // metric
    val metric = readByteBuffer.read
    // dimensions
    val numOfDim = readByteBuffer.getInt
    val dimensions = (for {
      _ <- 1 to numOfDim
      name  = readByteBuffer.read
      typ   = readByteBuffer.read
      value = new Array[Byte](readByteBuffer.getInt)
      _     = readByteBuffer.get(value)
    } yield (name, typ, value)).toList

    InsertNewEntry(ts = ts, metric = metric, dimensions = dimensions)
  }

  override def serialize(entry: InsertNewEntry): Array[Byte] =
    deserialize(ts = entry.ts, metric = entry.metric, dimensions = entry.dimensions)

  private def deserialize(ts: Long, metric: String, dimensions: List[(String, String, Array[Byte])]): Array[Byte] = {
    writeBuffer.clear()

    // timestamp
    writeBuffer.write(ts.toString)
    // metric
    writeBuffer.write(metric)
    // dimensions
    writeBuffer.putInt(dimensions.length)
    dimensions.foreach {
      case (name, typ, value) =>
        writeBuffer.write(name)
        writeBuffer.write(typ)
        writeBuffer.putInt(value.length)
        writeBuffer.put(value)
    }

    writeBuffer.array
  }
}

abstract class BaseBuffer(maxSize: Int) {

  protected val buffer = ByteBuffer.allocate(maxSize)
}

private class WriteBuffer(maxSize: Int) extends BaseBuffer(maxSize) {

  def array = buffer.array

  def clear() = buffer.clear()

  def put(v: Array[Byte]) = buffer.put(v)

  def putInt(v: Int) = buffer.putInt(v)

  def write(s: String): Unit = {
    val xs = s.getBytes
    putInt(xs.length)
    put(xs)
  }
}

private class ReadBuffer(maxSize: Int) extends BaseBuffer(maxSize) {

  def clear(array: Array[Byte]) = {
    buffer.clear()
    buffer.put(array)
    buffer.position(0)
  }

  def get(v: Array[Byte]): Unit = buffer.get(v)

  def getInt: Int = buffer.getInt

  def read: String = {
    val length = buffer.getInt
    val array  = new Array[Byte](length)
    buffer.get(array)
    new String(array)
  }
}