package jvstm.test.jwormbench;

import java.util.concurrent.Callable;


import jvstm.Transaction;
import jwormbench.core.AbstractStep;
import jwormbench.core.IStep;
import jwormbench.core.Direction;
import jwormbench.core.IOperation;
import jwormbench.core.IWorm;

public class JvstmStep extends AbstractStep{

  public JvstmStep(Direction direction, IOperation<?> op) {
    super(direction, op);
  }

  @Override
  public Object performStep(final IWorm worm) {
    Object res = null;
    //
    // Perform operation
    //
    try {
      Callable<Object> task = new Callable<Object>() {
        public Object call() throws Exception {
          return op.performOperation(worm);
        }
      };
      if(op.getKind().ordinal() < 5)
        res = Transaction.doIt(task, true);
      else 
        res = Transaction.doIt(task, false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    //
    // Move worm
    //
    worm.move(direction);
    worm.updateWorldUnderWorm();
    return res;
  }
}
