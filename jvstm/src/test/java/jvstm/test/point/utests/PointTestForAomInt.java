package jvstm.test.point.utests;

import jvstm.test.point.core.TestPoint;
import jvstm.test.point.impl.AomIntPointFactory;

public class PointTestForAomInt extends TestPoint<Integer>{

    public PointTestForAomInt() {
        super(new AomIntPointFactory());
    }

}
