package leakcanary

import leakcanary.HeapValue.BooleanValue
import leakcanary.HeapValue.ByteValue
import leakcanary.HeapValue.CharValue
import leakcanary.HeapValue.DoubleValue
import leakcanary.HeapValue.FloatValue
import leakcanary.HeapValue.IntValue
import leakcanary.HeapValue.LongValue
import leakcanary.HeapValue.ObjectReference
import leakcanary.HeapValue.ShortValue
import leakcanary.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord
import leakcanary.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord.FieldRecord
import leakcanary.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord.StaticFieldRecord
import leakcanary.Record.HeapDumpRecord.ObjectRecord.InstanceDumpRecord
import leakcanary.Record.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.BooleanArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.DoubleArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.FloatArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.IntArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.LongArrayDump
import leakcanary.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ShortArrayDump
import okio.BufferedSource
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Not thread safe, should be used from a single thread.
 */
open class HprofReader constructor(
  protected var source: BufferedSource,
  protected val startPosition: Long,
  val idSize: Int
) : Closeable {

  var position: Long = startPosition
    protected set

  val isOpen
    get() = source.isOpen

  private val typeSizes = mapOf(
      // object
      OBJECT_TYPE to idSize,
      BOOLEAN_TYPE to BOOLEAN_SIZE,
      CHAR_TYPE to CHAR_SIZE,
      FLOAT_TYPE to FLOAT_SIZE,
      DOUBLE_TYPE to DOUBLE_SIZE,
      BYTE_TYPE to BYTE_SIZE,
      SHORT_TYPE to SHORT_SIZE,
      INT_TYPE to INT_SIZE,
      LONG_TYPE to LONG_SIZE
  )

  fun readValue(type: Int): HeapValue {
    return when (type) {
      OBJECT_TYPE -> ObjectReference(readId())
      BOOLEAN_TYPE -> BooleanValue(readBoolean())
      CHAR_TYPE -> CharValue(readChar())
      FLOAT_TYPE -> FloatValue(readFloat())
      DOUBLE_TYPE -> DoubleValue(readDouble())
      BYTE_TYPE -> ByteValue(readByte())
      SHORT_TYPE -> ShortValue(readShort())
      INT_TYPE -> IntValue(readInt())
      LONG_TYPE -> LongValue(readLong())
      else -> throw IllegalStateException("Unknown type $type")
    }
  }

  fun typeSize(type: Int): Int {
    return typeSizes.getValue(type)
  }

  fun readShort(): Short {
    position += SHORT_SIZE
    return source.readShort()
  }

  fun readInt(): Int {
    position += INT_SIZE
    return source.readInt()
  }

  fun readIdArray(arrayLength: Int): LongArray {
    // TODO Read the array at once
    val array = LongArray(arrayLength)
    for (i in 0 until arrayLength) {
      array[i] = readId()
    }
    return array
  }

  fun readBooleanArray(arrayLength: Int): BooleanArray {
    // TODO Read the array at once
    val array = BooleanArray(arrayLength)
    readByteArray(BOOLEAN_SIZE * arrayLength).forEachIndexed { index, byte ->
      array[index] = byte != 0.toByte()
    }
    return array
  }

  fun readCharArray(arrayLength: Int): CharArray {
    // TODO Avoid creating a byte array
    val buffer = ByteBuffer.wrap(readByteArray(CHAR_SIZE * arrayLength))
        .asCharBuffer()
    val array = CharArray(buffer.remaining())
    buffer.get(array)
    return array
  }

  fun readFloatArray(arrayLength: Int): FloatArray {
    // TODO Avoid creating a byte array
    val buffer = ByteBuffer.wrap(readByteArray(FLOAT_SIZE * arrayLength))
        .asFloatBuffer()
    val array = FloatArray(buffer.remaining())
    buffer.get(array)
    return array
  }

  fun readDoubleArray(arrayLength: Int): DoubleArray {
    // TODO Avoid creating a byte array
    val buffer = ByteBuffer.wrap(readByteArray(DOUBLE_SIZE * arrayLength))
        .asDoubleBuffer()
    val array = DoubleArray(buffer.remaining())
    buffer.get(array)
    return array
  }

  fun readShortArray(arrayLength: Int): ShortArray {
    // TODO Avoid creating a byte array
    val buffer = ByteBuffer.wrap(readByteArray(SHORT_SIZE * arrayLength))
        .asShortBuffer()
    val array = ShortArray(buffer.remaining())
    buffer.get(array)
    return array
  }

  fun readIntArray(arrayLength: Int): IntArray {
    // TODO Avoid creating a byte array
    val buffer = ByteBuffer.wrap(readByteArray(INT_SIZE * arrayLength))
        .asIntBuffer()
    val array = IntArray(buffer.remaining())
    buffer.get(array)
    return array
  }

  fun readLongArray(arrayLength: Int): LongArray {
    // TODO Avoid creating a byte array
    val buffer = ByteBuffer.wrap(readByteArray(LONG_SIZE * arrayLength))
        .asLongBuffer()
    val array = LongArray(buffer.remaining())
    buffer.get(array)
    return array
  }

  fun readLong(): Long {
    position += LONG_SIZE
    return source.readLong()
  }

  fun exhausted() = source.exhausted()

  open fun skip(byteCount: Long) {
    position += byteCount
    return source.skip(byteCount)
  }

  fun readByte(): Byte {
    position += BYTE_SIZE
    return source.readByte()
  }

  fun readBoolean(): Boolean {
    position += BOOLEAN_SIZE
    return source.readByte() != 0.toByte()
  }

  fun readByteArray(byteCount: Int): ByteArray {
    position += byteCount
    return source.readByteArray(byteCount.toLong())
  }

  fun readChar(): Char {
    // TODO Avoid creating a byte array
    return ByteBuffer.wrap(readByteArray(CHAR_SIZE))
        .char
  }

  fun readFloat(): Float {
    return Float.fromBits(readInt())
  }

  fun readDouble(): Double {
    return Double.fromBits(readLong())
  }

  fun readId(): Long {
    // As long as we don't interpret IDs, reading signed values here is fine.
    return when (idSize) {
      1 -> readByte().toLong()
      2 -> readShort().toLong()
      4 -> readInt().toLong()
      8 -> readLong()
      else -> throw IllegalArgumentException("ID Length must be 1, 2, 4, or 8")
    }
  }

  fun readUtf8(byteCount: Long): String {
    position += byteCount
    return source.readUtf8(byteCount)
  }

  fun readUnsignedInt(): Long {
    return readInt().toLong() and INT_MASK
  }

  fun readUnsignedByte(): Int {
    return readByte().toInt() and BYTE_MASK
  }

  fun readUnsignedShort(): Int {
    return readShort().toInt() and 0xFFFF
  }

  fun skip(byteCount: Int) {
    position += byteCount
    return source.skip(byteCount.toLong())
  }

  override fun close() {
    source.close()
  }

  fun readInstanceDumpRecord(
    id: Long
  ): InstanceDumpRecord {
    val stackTraceSerialNumber = readInt()
    val classId = readId()
    val remainingBytesInInstance = readInt()
    val fieldValues = readByteArray(remainingBytesInInstance)

    return InstanceDumpRecord(
        id = id,
        stackTraceSerialNumber = stackTraceSerialNumber,
        classId = classId,
        fieldValues = fieldValues
    )
  }

  fun readClassDumpRecord(
    id: Long
  ): ClassDumpRecord {
    // stack trace serial number
    val stackTraceSerialNumber = readInt()
    val superClassId = readId()
    // class loader object ID
    val classLoaderId = readId()
    // signers object ID
    val signersId = readId()
    // protection domain object ID
    val protectionDomainId = readId()
    // reserved
    readId()
    // reserved
    readId()

    // instance size (in bytes)
    // Useful to compute retained size
    val instanceSize = readInt()

    // Skip over the constant pool
    val constantPoolCount = readUnsignedShort()
    for (i in 0 until constantPoolCount) {
      // constant pool index
      skip(SHORT_SIZE)
      skip(typeSize(readUnsignedByte()))
    }

    val staticFields = mutableListOf<StaticFieldRecord>()
    val staticFieldCount = readUnsignedShort()
    for (i in 0 until staticFieldCount) {

      val nameStringId = readId()
      val type = readUnsignedByte()
      val value = readValue(type)

      staticFields.add(
          StaticFieldRecord(
              nameStringId = nameStringId,
              type = type,
              value = value
          )
      )
    }

    val fields = mutableListOf<FieldRecord>()
    val fieldCount = readUnsignedShort()
    for (i in 0 until fieldCount) {
      fields.add(FieldRecord(nameStringId = readId(), type = readUnsignedByte()))
    }

    return ClassDumpRecord(
        id = id,
        stackTraceSerialNumber = stackTraceSerialNumber,
        superClassId = superClassId,
        classLoaderId = classLoaderId,
        signersId = signersId,
        protectionDomainId = protectionDomainId,
        instanceSize = instanceSize,
        staticFields = staticFields,
        fields = fields
    )
  }

  fun readObjectArrayDumpRecord(
    id: Long
  ): ObjectArrayDumpRecord {
    // stack trace serial number
    val stackTraceSerialNumber = readInt()
    val arrayLength = readInt()
    val arrayClassId = readId()
    val elementIds = readIdArray(arrayLength)
    return ObjectArrayDumpRecord(
        id = id,
        stackTraceSerialNumber = stackTraceSerialNumber,
        arrayClassId = arrayClassId,
        elementIds = elementIds
    )
  }

  fun readPrimitiveArrayDumpRecord(
    id: Long
  ): PrimitiveArrayDumpRecord {
    val stackTraceSerialNumber = readInt()
    // length
    val arrayLength = readInt()
    val type = readUnsignedByte()
    return when (type) {
      BOOLEAN_TYPE -> BooleanArrayDump(
          id, stackTraceSerialNumber, readBooleanArray(arrayLength)
      )
      CHAR_TYPE -> CharArrayDump(
          id, stackTraceSerialNumber, readCharArray(arrayLength)
      )
      FLOAT_TYPE -> FloatArrayDump(
          id, stackTraceSerialNumber, readFloatArray(arrayLength)
      )
      DOUBLE_TYPE -> DoubleArrayDump(
          id, stackTraceSerialNumber, readDoubleArray(arrayLength)
      )
      BYTE_TYPE -> ByteArrayDump(
          id, stackTraceSerialNumber, readByteArray(arrayLength)
      )
      SHORT_TYPE -> ShortArrayDump(
          id, stackTraceSerialNumber, readShortArray(arrayLength)
      )
      INT_TYPE -> IntArrayDump(
          id, stackTraceSerialNumber, readIntArray(arrayLength)
      )
      LONG_TYPE -> LongArrayDump(
          id, stackTraceSerialNumber, readLongArray(arrayLength)
      )
      else -> throw IllegalStateException("Unexpected type $type")
    }
  }

  val tagPositionAfterReadingId
    get() = position - (idSize + BYTE_SIZE)

  companion object {
    const val BOOLEAN_SIZE = 1
    const val CHAR_SIZE = 2
    const val FLOAT_SIZE = 4
    const val DOUBLE_SIZE = 8
    const val BYTE_SIZE = 1
    const val SHORT_SIZE = 2
    const val INT_SIZE = 4
    const val LONG_SIZE = 8

    const val OBJECT_TYPE = 2
    const val BOOLEAN_TYPE = 4
    const val CHAR_TYPE = 5
    const val FLOAT_TYPE = 6
    const val DOUBLE_TYPE = 7
    const val BYTE_TYPE = 8
    const val SHORT_TYPE = 9
    const val INT_TYPE = 10
    const val LONG_TYPE = 11

    const val INT_MASK = 0xffffffffL
    const val BYTE_MASK = 0xff
  }

}
