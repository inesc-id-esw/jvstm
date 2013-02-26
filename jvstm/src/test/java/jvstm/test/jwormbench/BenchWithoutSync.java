package jvstm.test.jwormbench;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jwormbench.config.params.NrOfIterations;
import jwormbench.config.params.NrOfThreads;
import jwormbench.config.params.StepsConfigFile;
import jwormbench.config.params.TimeOut;
import jwormbench.config.params.WorldConfigFile;
import jwormbench.config.params.WormsConfigFile;
import jwormbench.core.IWorld;
import jwormbench.defaults.World;
import jwormbench.defaults.DefaultNodeFactory;
import jwormbench.defaults.DefaultCoordinateFactory;
import jwormbench.defaults.DefaultOperationFactory;
import jwormbench.defaults.DefaultStepFactory;
import jwormbench.defaults.DefaultWormFactory;
import jwormbench.factories.INodeFactory;
import jwormbench.factories.ICoordinateFactory;
import jwormbench.factories.IOperationFactory;
import jwormbench.factories.IStepFactory;
import jwormbench.factories.IWormFactory;
import jwormbench.setup.WorldFileLoader;
import jwormbench.setup.IWorlSetup;
import jwormbench.setup.IStepSetup;
import jwormbench.setup.IWormsSetup;
import jwormbench.setup.StepsFileLoader;
import jwormbench.setup.WormsFileLoader;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class BenchWithoutSync extends AbstractModule{
  private final int nrOfIterations, nrOfThreads, timeOut;
  private final String configWorms, configWorld, configOperations;

  public BenchWithoutSync(int nrOfIterations, int nrOfThreads, int timeOut,
      String configWorms, String configWorls, String configOperations) {
    super();
    this.nrOfIterations = nrOfIterations;
    this.nrOfThreads = nrOfThreads;
    this.timeOut = timeOut;
    this.configWorms = configWorms;
    this.configWorld = configWorls;
    this.configOperations = configOperations;
    Logger.getLogger("").getHandlers()[0].setFormatter(new Formatter() {
      public String format(LogRecord record) {
        return record.getMessage();
      }
    });
  }
  @Override
  protected void configure() {
    //
    // Coordinates
    //
    bind(ICoordinateFactory.class)
      .to(DefaultCoordinateFactory.class)
      .in(Singleton.class);
    //
    // Parameters
    //
    bind(int.class).annotatedWith(NrOfIterations.class).toInstance(nrOfIterations);
    bind(int.class).annotatedWith(NrOfThreads.class).toInstance(nrOfThreads);
    bind(int.class).annotatedWith(TimeOut.class).toInstance(timeOut);
    //
    // Steps and Operations
    //
    bind(String.class)
      .annotatedWith(StepsConfigFile.class)
      .toInstance(configOperations);
    bind(IOperationFactory.class)
      .to(DefaultOperationFactory.class)
      .in(Singleton.class);
    bind(IStepSetup.class)
      .to(StepsFileLoader.class)
      .in(Singleton.class);
    bind(IStepFactory.class)
      .to(DefaultStepFactory.class)
      .in(Singleton.class);
    //
    // World
    //
    bind(String.class)
      .annotatedWith(WorldConfigFile.class)
      .toInstance(configWorld);
    bind(IWorlSetup.class)
      .to(WorldFileLoader.class)
      .in(Singleton.class);
    bind(INodeFactory.class)
      .to(DefaultNodeFactory.class)
      .in(Singleton.class);
    bind(IWorld.class)
      .to(World.class)
      .in(Singleton.class);
    //
    // Worms
    //
    bind(String.class)
      .annotatedWith(WormsConfigFile.class)
      .toInstance(configWorms);
    bind(IWormsSetup.class)
      .to(WormsFileLoader.class)
      .in(Singleton.class);
    bind(IWormFactory.class)
      .to(DefaultWormFactory.class)
      .in(Singleton.class);
  }
}
