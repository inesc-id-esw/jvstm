package jvstm;

public interface TransactionFactory {
    Transaction makeTopLevelTransaction(int txNumber);
}
