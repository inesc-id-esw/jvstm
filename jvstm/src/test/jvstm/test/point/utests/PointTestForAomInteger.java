package jvstm.test.point.utests;

import jvstm.test.point.core.TestPoint;
import jvstm.test.point.impl.AomIntPointFactory;
import jvstm.test.point.impl.AomIntegerPointFactory;

public class PointTestForAomInteger extends TestPoint<Integer>{

    public PointTestForAomInteger() {
	super(new AomIntegerPointFactory());
    }

}
