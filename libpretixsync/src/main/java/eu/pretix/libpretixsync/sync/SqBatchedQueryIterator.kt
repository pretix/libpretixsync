package eu.pretix.libpretixsync.sync

class SqBatchedQueryIterator<K, T>(
    private var allParameters: Iterator<K>,
    private var callBack: BatchedQueryCall<K, T>,
) : Iterator<T> {
    /**
     * An iterator that performs a query with an arbitrary number of parameters of the same type,
     * such as a WHERE a IN (…) query with an unknown number of values. We can't just use IN (…)
     * naively, since SQLite has a limit on 999 variables per query (SQLITE_MAX_VARIABLE_NUMBER),
     * so we need to do it in batches.
     */
    private val BATCH_SIZE = 500
    private val buffer: MutableList<T> = ArrayList()

    interface BatchedQueryCall<K, T> {
        fun runBatch(parameterBatch: List<K>): List<T>
    }

    override fun hasNext(): Boolean {
        return buffer.size > 0 || allParameters.hasNext()
    }

    override fun next(): T {
        if (buffer.size == 0) {
            val batch: MutableList<K> = ArrayList()
            for (i in 0 until BATCH_SIZE) {
                if (allParameters.hasNext()) {
                    batch.add(allParameters.next())
                } else {
                    break
                }
            }
            val batchResult = callBack.runBatch(batch)
            buffer.addAll(batchResult)

            if (buffer.size == 0) {
                throw BatchEmptyException()
            }
        }
        return buffer.removeAt(0)
    }
}
