package jvstm;

/* Classes implementing this interface (currently VBoxBody and VArrayLogNode) can be cleaned
 * by the JVSTM garbage collection algorithm, when passed to an ActiveTransactionsRecord.
 */
public interface GarbageCollectable {
    public void clearPrevious();
}
