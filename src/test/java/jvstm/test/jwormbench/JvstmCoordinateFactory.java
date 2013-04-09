package jvstm.test.jwormbench;

import jwormbench.core.ICoordinate;
import jwormbench.factories.ICoordinateFactory;

public class JvstmCoordinateFactory implements ICoordinateFactory {
        /**
         * @see jwormbench.factories.ICoordinateFactory#make(int, int)
         */
        public ICoordinate make(int x, int y){
          return new JvstmCoordinate(x, y);
        }
}
