package eu.pretix.libpretixsync.sync;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.requery.util.CloseableIterator;

public class BatchedQueryIterator<K, T> implements Iterator<T> {
    /**
     * An iterator that performs a query with an arbitrary number of parameters of the same type,
     * such as a WHERE a IN (…) query with an unknown number of values. We can't just use IN (…)
     * naively, since SQLite has a limit on 999 variables per query (SQLITE_MAX_VARIABLE_NUMBER),
     * so we need to do it in batches.
     */

    private static final int BATCH_SIZE = 500;
    private Iterator<K> allParameters;
    private List<T> buffer = new ArrayList<>();
    private BatchedQueryCall<K, T> callBack;

    public interface BatchedQueryCall<K, T> {
        public CloseableIterator<T> runBatch(List<K> parameterBatch);
    }

    public BatchedQueryIterator(Iterator<K> allParameters, BatchedQueryCall<K, T> callBack) {
        this.allParameters = allParameters;
        this.callBack = callBack;
    }

    @Override
    public boolean hasNext() {
        return buffer.size() > 0 || allParameters.hasNext();
    }

    @Override
    public T next() {
        if (buffer.size() == 0) {
            List<K> batch = new ArrayList<>();
            for (int i = 0; i < BATCH_SIZE; i++) {
                if (allParameters.hasNext()) {
                    batch.add(allParameters.next());
                } else {
                    break;
                }
            }
            CloseableIterator<T> batchResult = callBack.runBatch(batch);
            while (batchResult.hasNext()) {
                buffer.add(batchResult.next());
            }
            batchResult.close();
            if (buffer.size() == 0) {
                throw new BatchEmptyException();
            }
        }
        return buffer.remove(0);
    }
}
