package jvstm.test.jwormbench;

import jwormbench.factories.ICoordinateFactory;
import jwormbench.factories.INodeFactory;
import jwormbench.factories.IStepFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class JvstmSyncModule extends AbstractModule{
  static{
    //
    // Print number of aborted transactions
    //
      /*
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        System.out.println("Nr of aborted trxs: " + Transaction.nrOfAborts);;
      }
    });
    */
  }
  @Override
  protected void configure() {
    bind(IStepFactory.class)
    .to(JvstmStepFactory.class)
    .in(Singleton.class);

    bind(INodeFactory.class)
    .to(JvstmBenchNodeFactory.class)
    .in(Singleton.class);

    bind(ICoordinateFactory.class)
    .to(JvstmCoordinateFactory.class)
    .in(Singleton.class);

  }
}
