package jvstm.test.point.utests;

import jvstm.test.point.core.TestPoint;
import jvstm.test.point.impl.AomIntPointFactory;

public class PointTestForAomDouble extends TestPoint<Integer>{

    public PointTestForAomDouble() {
	super(new AomIntPointFactory());
    }

}
