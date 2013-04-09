import jvstm.*;

public class TestNested {

    static void showBox(VBox box) {
        System.out.println("Value for box " + box + " -> " + box.get());
    }


    public static void main(String[] args) {
        Transaction.transactionallyDo(new TransactionalCommand() {
                public void doIt() {
                    final VBox<Long> b1 = new VBox<Long>();
                    final VBox<Long> b2 = new VBox<Long>();
                    b1.put(1L);
                    b2.put(2L);
                    
                    showBox(b1);
                    showBox(b2);
                    
                    try {
                        Transaction.transactionallyDo(new TransactionalCommand() {
                                public void doIt() {
                                    try {
                                        Transaction.transactionallyDo(new TransactionalCommand() {
                                                public void doIt() {
                                                    b1.put(b1.get() + 3);
                                                    b2.put(b2.get() + 6);
                                                    throw new Error("Some");
                                                }
                                            });
                                    } catch (Throwable e) { }
                                    
                                    showBox(b1);
                                    showBox(b2);
                                    
                                    Transaction.transactionallyDo(new TransactionalCommand() {
                                            public void doIt() {
                                                b1.put(b1.get() + 3);
                                                b2.put(b2.get() + 6);
                                            }
                                        });
                                    
                                    showBox(b1);
                                    showBox(b2);
                                    throw new Error("Ola");
                                }
                            });
                        
                    } catch (Throwable e) { }
                    
                    showBox(b1);
                    showBox(b2);
                }
            });
    }
}
