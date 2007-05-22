
import java.util.Set;
import java.util.Iterator;

import jvstm.Atomic;
import jvstm.VBox;
import jvstm.VBoxInt;

import jvstm.util.VLinkedSet;

import jvstm.cps.ConsistencyException;
import jvstm.cps.ConsistencyPredicate;
import jvstm.cps.ConsistencyPredicateSystem;

public class SimpleTests {

    public static void main(String[] args) {
        ConsistencyPredicateSystem.initialize();

        SimpleTests tests = new SimpleTests();
        tests.run();
    }

    public void run() {
        BagOfValues bag = makeBag();

        addToBag(bag, NUMBER_CREATOR, 1, 2, 3, 4);
        addToBag(bag, EVEN_CREATOR, 2, 4, 6, 8);

        addToBag(bag, EVEN_CREATOR, 1, 2, 3);
        
        addToBag(bag, NUMBER_CREATOR, -100);

        addToBag(bag, NUMBER_CREATOR, 2000);

        addToBag(bag, NUMBER_CREATOR, -100);

        addToBag(bag, NUMBER_CREATOR, -1500);

        addToBag(bag, EVEN_CREATOR, 10, 20, 30, 15);


        addToValue(bag, 2000, 100);
        addToValue(bag, -100, -300);
        addToValue(bag, 8, 1);
        addToValue(bag, 2100, -100);
        addToValue(bag, -400, -700);
        addToValue(bag, 6, 10);
        addToValue(bag, 2000, -2000);
        addToValue(bag, 16, 10);

        removeValue(bag, 26);
        removeValue(bag, 2000);
        removeValue(bag, 1);

        addToBag(bag, NUMBER_CREATOR, 500, 600, 700);

        removeValue(bag, 2000);
        removeValue(bag, -400);
        removeValue(bag, 0);
    }


    @Atomic
    public BagOfValues makeBag() {
        return new BagOfValues();
    }

    public void removeValue(BagOfValues bag, int value) {
        bag.printSummary();
        System.out.printf("Will try to remove the value %d\n", value);

        try {
            atomicRemoveValue(bag, value);
            System.out.println("+++++ It worked!");
        } catch (ConsistencyException ce) {
            System.out.println("----- Failed because of a consistency exception: " + ce.getMethodName());
        }
    }

    @Atomic
    public void atomicRemoveValue(BagOfValues bag, int value) {
        Iterator<Number> iter = bag.getNumbers().iterator();
        while (iter.hasNext()) {
            if (iter.next().getValue() == value) {
                iter.remove();
                return;
            }
        }
    }

    public void addToValue(BagOfValues bag, int value, int valueToAdd) {
        bag.printSummary();
        System.out.printf("Will try to add %d to the value %d\n", valueToAdd, value);

        try {
            atomicAddToValue(bag, value, valueToAdd);
            System.out.println("+++++ It worked!");
        } catch (ConsistencyException ce) {
            System.out.println("----- Failed because of a consistency exception: " + ce.getMethodName());
        }
    }

    @Atomic
    public void atomicAddToValue(BagOfValues bag, int value, int valueToAdd) {
        for (Number num : bag.getNumbers()) {
            if (num.getValue() == value) {
                num.update(valueToAdd);
                return;
            }
        }
    }

    public void addToBag(BagOfValues bag, NumCreator numCreator, Integer... values) {
        bag.printSummary();
        System.out.printf("Will try to add %s elements with values: ", numCreator.name());
        for (int val : values) {
            System.out.printf("%d, ", val);
        }
        System.out.println();

        try {
            atomicAdd(bag, numCreator, values);
            System.out.println("+++++ It worked!");
        } catch (ConsistencyException ce) {
            System.out.println("----- Failed because of a consistency exception: " + ce.getMethodName());
        }
    }

    @Atomic
    public void atomicAdd(BagOfValues bag, NumCreator numCreator, Integer[] values) {
        for (Integer val : values) {
            bag.addValue(numCreator.create(val));
        }
    }


    interface NumCreator {
        public Number create(int value);
        public String name();
    }

    static final NumCreator NUMBER_CREATOR = new NumCreator() {
            public Number create(int val) {
                return new Number(val);
            }

            public String name() {
                return "Number";
            }
        };

    static final NumCreator EVEN_CREATOR = new NumCreator() {
            public Number create(int val) {
                return new Even(val);
            }

            public String name() {
                return "Even";
            }
        };

    static class Number {
        private VBoxInt value = new VBoxInt(0);

        public Number(int value) {
            ConsistencyPredicateSystem.registerNewObject(this);            
            this.value.putInt(value);
        }

        public void update(int delta) {
            this.value.inc(delta);
        }

        public int getValue() {
            return this.value.getInt();
        }

        @ConsistencyPredicate
        public boolean greaterThanMinus1000() {
            return (getValue() > -1000);
        }
    }


    static class Even extends Number {
        public Even(int value) {
            super(value);
        }

        @ConsistencyPredicate
        public boolean isEven() {
            return (getValue() % 2) == 0;
        }
    }


    static class BagOfValues {
        private VLinkedSet<Number> values = new VLinkedSet<Number>();

        public BagOfValues() {
            ConsistencyPredicateSystem.registerNewObject(this);
        }

        public void addValue(Number value) {
            this.values.add(value);
        }

        public Set<Number> getNumbers() {
            return values;
        }

        public int getTotal() {
            int total = 0;

            for (Number num : values) {
                total += num.getValue();
            }
            
            return total;
        }

        public void printSummary() {
            System.out.printf("Current bag total value is: %d   (", getTotal());
            for (Number val : values) {
                System.out.printf("%d, ", val.getValue());
            }
            System.out.println(")");
        }

        @ConsistencyPredicate
        public boolean nonNegative() {
            return (getTotal() >= 0);
        }
    }
}
