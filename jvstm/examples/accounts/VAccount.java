import jvstm.*;
import pt.ist.esw.atomicannotation.Atomic;

class VAccount implements Account {
    private VBox<Long> balance = new VBox<Long>();

    VAccount(long balance) {
        setBalance(balance);
    }

    public long getBalance() {
        return balance.get();
    }

    void setBalance(long newBalance) {
        this.balance.put(newBalance);
    }

    public @Atomic void withdraw(long amount) {
        setBalance(getBalance() - amount);
    }

    public @Atomic void deposit(long amount) {
        setBalance(getBalance() + amount);
    }

    public boolean canWithdraw(long amount) {
        return amount < getBalance();
    }
}
