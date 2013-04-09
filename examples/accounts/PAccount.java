class PAccount implements Account {
    long balance;
    
    PAccount(long balance) {
        this.balance = balance;
    }

    public long getBalance() {
        return balance;
    }

    void setBalance(long newBalance) {
        this.balance = newBalance;
    }

    public void withdraw(long amount) {
        setBalance(getBalance() - amount);
    }

    public void deposit(long amount) {
        setBalance(getBalance() + amount);
    }

    public boolean canWithdraw(long amount) {
        return amount < getBalance();
    }
}
