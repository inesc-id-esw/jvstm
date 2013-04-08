/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm.gc;

// import java.util.concurrent.Executors;
// import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jvstm.ActiveTransactionsRecord;
import jvstm.Transaction;

public class GCTask implements Runnable {
    private ActiveTransactionsRecord lastCleanedRecord;
    private ThreadPoolExecutor cleanersPool = makeCleanersPool();

    private static ThreadPoolExecutor makeCleanersPool() {
        ThreadFactory fact = new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }};

        ThreadPoolExecutor executor = null;
        int poolSize = Runtime.getRuntime().availableProcessors() / 10 + 1;
        // if (poolSize > 40) {
        //     poolSize = 40;
        // }
        // poolSize = 5;
        executor = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                                          new LinkedBlockingQueue<Runnable>(), fact);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public GCTask(ActiveTransactionsRecord lastCleanedRecord) {
        this.lastCleanedRecord = lastCleanedRecord;
    }

    public void run() {
        while(true) {
            try {Thread.sleep(500);} catch (Exception e) {}
            ActiveTransactionsRecord rec = findOldestRecordInUse();

            /* take your pick: either launch a Task to clean each record or launch a task to clean a sequence of records... */

            //cleanUnusedRecords(rec);
            if (rec.transactionNumber > this.lastCleanedRecord.transactionNumber) {
                cleanersPool.execute(new MultipleCleanTask(this.lastCleanedRecord, rec));
                this.lastCleanedRecord = rec;
            }
        }
    }

    /**
     * This method is used for unit tests purpose to force GC running and
     * convert objects to the CompactLayout, when using the AOM approach.
     * In this case we should also have disabled the previous asynchronous
     * task through the VM property: -Djvstm.gc.disabled=true
     */
    public void runGc(){
        ActiveTransactionsRecord rec = findOldestRecordInUse();
        if (rec.transactionNumber > this.lastCleanedRecord.transactionNumber) {
            new MultipleCleanTask(this.lastCleanedRecord, rec).run();
            this.lastCleanedRecord = rec;
        }
    }

    // used to pass state between two calls of findOldestRecordUpTo()
    private TxContext oldestContext = null;

    private ActiveTransactionsRecord findOldestRecordInUse() {
        // We use this in case there are no thread running, to know until where to clean.  If we
        // only read this after doing the search we might clean more than we should, because a new
        // transaction can begin and commit a new record at any time.  By reading first, we ensure
        // that if all threads have gone, we can clean at least until here.
        ActiveTransactionsRecord mostRecentCommittedAtBegin = Transaction.mostRecentCommittedRecord;
        for (ActiveTransactionsRecord next = mostRecentCommittedAtBegin.getNext();
             (next != null) && next.isCommitted(); next = next.getNext()) {
             mostRecentCommittedAtBegin = next;
        } // we could use this opportunity to advance Transaction.mostRecentCommittedRecord

        // First pass.  Here we check all contexts to identify the oldest record in use.
        ActiveTransactionsRecord minRequiredRecord1 = findOldestRecordUpTo(null, Integer.MAX_VALUE);

        // If there was no record identified as a minimum we can safely clean up to the record that
        // was committed at the beginning, because all other threads will see it and use it (or use
        // another more recent record which is ok)
        if (minRequiredRecord1 == null) {
            return mostRecentCommittedAtBegin;
        }

        // Otherwise we do a second pass.  In the second pass we re-check all the records that were
        // checked before the identified oldest context, as they may have changed concurrently to a
        // lower minimum.
        ActiveTransactionsRecord minRequiredRecord2 = findOldestRecordUpTo(this.oldestContext,
                                                                           minRequiredRecord1.transactionNumber);

        // If we find another record in the second pass then that is the minimum.  If not then the
        // first found is it.
        return (minRequiredRecord2 != null) ? minRequiredRecord2 : minRequiredRecord1;
    }

    private ActiveTransactionsRecord findOldestRecordUpTo(TxContext limitContext, int minRequiredVersion) {
        ActiveTransactionsRecord minRequiredRecord = null;

        TxContext previousCtx = null;
        TxContext currentCtx = Transaction.allTxContexts;
        while (currentCtx != limitContext) {
            // remove a dead context if possible
            if ((currentCtx.owner.get() == null) && (currentCtx.next != null)) {
                removeCtx(previousCtx, currentCtx);
                currentCtx = currentCtx.next; // previous does not advance
                continue;
            }

            // we REALLY need this local variable, because of concurrent updates
            ActiveTransactionsRecord record = currentCtx.oldestRequiredVersion;
            if ((record != null) && (record.transactionNumber < minRequiredVersion)) {
                minRequiredVersion = record.transactionNumber;
                minRequiredRecord = record;
                this.oldestContext = currentCtx;
            }

            previousCtx = currentCtx;
            currentCtx = currentCtx.next;
        }
        return minRequiredRecord;
    }

    // this method does not check whether there is another context next of 'toRemove', so the caller
    // should be careful not to accidentally remove the last existing record.  Simply put: do no
    // invoke this method when 'toRemove.next' can return null.
    private void removeCtx(TxContext previous, TxContext toRemove) {
        if (previous == null) { // 'toRemove' is the first record
            Transaction.allTxContexts = toRemove.next;
        } else {
            previous.next = toRemove.next;
        }
    }

    // public static int total = 0;
    // public static int count = 0;
    // public static int max = 0;
    private void cleanUnusedRecords(ActiveTransactionsRecord upToThisRecord) {
        // int diff = (upToThisRecord.transactionNumber - this.lastCleanedRecord.transactionNumber);
        // total += diff;
        // if (diff != 0) count++;
        // if (diff > max) max = diff;
        while (this.lastCleanedRecord.transactionNumber < upToThisRecord.transactionNumber) {
            ActiveTransactionsRecord toClean = this.lastCleanedRecord.getNext();
            this.lastCleanedRecord = toClean;
            cleanersPool.execute(new CleanTask(toClean));
        }
    }

    private static class CleanTask implements Runnable {
        ActiveTransactionsRecord rec;

        public CleanTask(ActiveTransactionsRecord rec) {
            this.rec = rec;
        }

        public void run() {
            this.rec.clean();
        }
    }

    private static class MultipleCleanTask implements Runnable {
        ActiveTransactionsRecord lastCleaned, upToThis;

        public MultipleCleanTask(ActiveTransactionsRecord lastCleaned, ActiveTransactionsRecord upToThis) {
            this.lastCleaned = lastCleaned;
            this.upToThis = upToThis;
        }

        public void run() {
            while (this.lastCleaned.transactionNumber < upToThis.transactionNumber) {
                this.lastCleaned = this.lastCleaned.getNext();
                this.lastCleaned.clean();
            }
        }
    }

    // // This executor does not create another thread to do the cleaning.  It is used in scenarios
    // // where the processor count is low enough, so that it does not justify to start a new thread
    // private static class SingleThreadedCleaner extends ThreadPoolExecutor {
    //  public SingleThreadedCleaner() {
    //      super(1, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    //  }

    //  public void execute(Runnable r) {
    //      r.run();
    //  }
    // }
}
