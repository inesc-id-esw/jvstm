

public class TestNested {

    static void showBox(VBox box) {
        System.out.println("Value for box " + box + " -> " + box.getValue());
    }


    public static void main(String[] args) {
        Transaction.transactionallyDo(new TransactionalCommand() {
                public void doIt() {
                    final VBox<Long> b1 = new VBox<Long>();
                    final VBox<Long> b2 = new VBox<Long>();
                    b1.setValue(1L);
                    b2.setValue(2L);
                    
                    showBox(b1);
                    showBox(b2);
                    
                    try {
                        Transaction.transactionallyDo(new TransactionalCommand() {
                                public void doIt() {
                                    try {
                                        Transaction.transactionallyDo(new TransactionalCommand() {
                                                public void doIt() {
                                                    b1.setValue(b1.getValue() + 3);
                                                    b2.setValue(b2.getValue() + 6);
                                                    throw new Error("Some");
                                                }
                                            });
                                    } catch (Throwable e) { }
                                    
                                    showBox(b1);
                                    showBox(b2);
                                    
                                    Transaction.transactionallyDo(new TransactionalCommand() {
                                            public void doIt() {
                                                b1.setValue(b1.getValue() + 3);
                                                b2.setValue(b2.getValue() + 6);
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
