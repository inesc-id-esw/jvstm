import jvstm.PerTxBox;
import jvstm.VBox;


public class SwapperPerTxBox {

	public SwapperPerTxBox(int initialX, int initialY) {
		this.x = new VBox<Integer>(initialX);
		this.y = new VBox<Integer>(initialY);
	}
	
	private VBox<Integer> x;
	private VBox<Integer> y;
	private PerTxBox<Boolean> swapped = new PerTxBox<Boolean>(false){
		@Override
		public void commit(Boolean value) {
			if (value) {
				int tmp = x.get();
				int tmp2 = y.get();
				x.put(tmp2);
				y.put(tmp);
				if (tmp == tmp2) {
					System.out.println("Inconsistency in detail!");
				}
			}
		}
	};
	
	public void swapXY() {
		swapped.put(!swapped.get());
	}

	public int getX() {
		if (swapped.get())
			return y.get();
		
		return x.get();
	}

	public int getY() {
		if (swapped.get())
			return x.get();
		
		return y.get();
	}
	
}
