package jvstm.test.jwormbench;

import com.google.inject.Inject;

import jwormbench.core.IStep;
import jwormbench.core.Direction;
import jwormbench.core.IOperation;
import jwormbench.factories.AbstractStepFactory;
import jwormbench.factories.IOperationFactory;
import jwormbench.setup.IStepSetup;

public class JvstmStepFactory extends AbstractStepFactory{
  @Inject
  public JvstmStepFactory(IStepSetup opsSetup, IOperationFactory opFac) {
    super(opsSetup, opFac);
  }

  @Override
  protected IStep factoryMethod(IOperation<?> op, Direction direction) {
    return new JvstmStep(direction, op);
  }

}
