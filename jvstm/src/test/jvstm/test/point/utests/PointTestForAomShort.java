package jvstm.test.point.utests;

import jvstm.test.point.core.TestPoint;
import jvstm.test.point.impl.AomIntPointFactory;
import jvstm.test.point.impl.AomShortPointFactory;

public class PointTestForAomShort extends TestPoint<Short>{

    public PointTestForAomShort() {
	super(new AomShortPointFactory());
    }

}
