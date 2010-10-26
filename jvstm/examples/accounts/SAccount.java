class SAccount implements Account {
    long balance;
    
    SAccount(long balance) {
        this.balance = balance;
    }

    public synchronized long getBalance() {
        return balance;
    }

    synchronized void setBalance(long newBalance) {
        this.balance = newBalance;
    }

    synchronized public void withdraw(long amount) {
        setBalance(getBalance() - amount);
    }

    synchronized public void deposit(long amount) {
        setBalance(getBalance() + amount);
    }

    public boolean canWithdraw(long amount) {
        return amount < getBalance();
    }
}
