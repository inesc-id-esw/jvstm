public class CFOCounter implements Counter {
    private VBox<Long> count = new VBox<Long>(0L);

    private PerTxBox<Long> toAdd = new PerTxBox<Long>(0L) {
        public void commit(Long value) {
            count.put(count.get() + value);
        }
    };

    public @Atomic void inc() {
        toAdd.put(toAdd.get() + 1);
    }

    public long getCount() {
        return count.get() + toAdd.get();
    }
}
