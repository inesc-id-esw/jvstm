import jvstm.PerTxBox;
import jvstm.VBox;


public class CounterPerTxBox {

	public CounterPerTxBox(long initialValue) {
		this.count = new VBox<Long>(initialValue);
	}
	
	private VBox<Long> count;
	private PerTxBox<Long> countPerTxBox = new PerTxBox<Long>(0L) {
		@Override
		public void commit(Long value) {
			count.put(count.get() + value);
		}
	};

	public long getCount() {
		return count.get() + countPerTxBox.get();
	}

	public void inc() {
		countPerTxBox.put(countPerTxBox.get() + 1);
	}

}
