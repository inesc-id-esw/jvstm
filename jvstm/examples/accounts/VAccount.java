class VAccount implements Account {
    private VBox<Long> balance = new VBox<Long>();

    VAccount(long balance) {
        setBalance(balance);
    }

    public long getBalance() {
        return balance.getValue();
    }

    void setBalance(long newBalance) {
        this.balance.setValue(newBalance);
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
