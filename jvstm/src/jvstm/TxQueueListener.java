package jvstm;

public interface TxQueueListener {
    public void noteOldestTransaction(int previousOldest, int newOldest);
}
