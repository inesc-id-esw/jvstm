package jvstm.test.jwormbench;

import java.util.logging.Logger;


import junit.framework.Assert;
import jvstm.ActiveTransactionsRecord;
import jvstm.Transaction;
import jvstm.VBox;
import jwormbench.core.IWorld;
import jwormbench.core.WormBench;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class RunJWormBench {
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final String EXEC_PATH = "";
    private static final String OPERATIONS_FILENAME_PATTERN = EXEC_PATH + "examples/tests/config/%d_ops_%d%%writes.txt";
    private static final String WORLD_FILENAME_PATTERN = EXEC_PATH + "examples/tests/config/%d.txt";
    private static final String WORMS_FILENAME_PATTERN = EXEC_PATH + "examples/tests/config/W-B[1.1]-H[%s]-%d.txt";
    private static final int worldSize = 512;
    private static final String headSize = "2.16";
    private static final int nrOperations = 1920;

    public static void performTest(int nrOfIterations, int nrOfThreads, int wRate) throws InterruptedException{
        final String configWorms = String.format(WORMS_FILENAME_PATTERN, headSize, worldSize);
        final String configWorld= String.format(WORLD_FILENAME_PATTERN, worldSize);
        final String configOperations = String.format(OPERATIONS_FILENAME_PATTERN, nrOperations, wRate);

        Module configModule = new BenchWithoutSync(
                nrOfIterations,
                nrOfThreads,
                60, // timeOut
                configWorms,
                configWorld,
                configOperations
        );
        //
        // Get instances
        //
        configModule = Modules.override(configModule).with(new JvstmSyncModule());
        Injector injector = Guice.createInjector(configModule );
        WormBench benchRollout = injector.getInstance(WormBench.class);
        IWorld world = injector.getInstance(IWorld.class);
        Logger logger = injector.getInstance(Logger.class);
        //
        // Run benchmark
        //
        logger.info("-----------------------------------------------------" + NEW_LINE);
        String syncStat = "JVSTM-lock-free; wrate = " + wRate;
        benchRollout.RunBenchmark(syncStat);
        //
        // Print results
        //
        benchRollout.LogExecutionTime();
        printNrOfObjectsExtendedAnStandard(logger, benchRollout);
        int finalWorldSum = world.getSumOfAllNodes() - benchRollout.getAccumulatedDiffOnWorld();
        Assert.assertEquals(benchRollout.initWorldSum, finalWorldSum);
    }
    private static void printNrOfObjectsExtendedAnStandard(Logger logger, WormBench benchRollout ){

        IWorld world = benchRollout.world;
        int nrObjectsNormal = 0, nrObjectsExtended = 0;
        for (int i = 0; i < world.getRowsNum(); i++) {
            for (int j = 0; j < world.getColumnsNum(); j++) {
                VBox node = (VBox) world.getNode(i, j);
                if(node.body != null)
                    nrObjectsExtended++;
                else
                    nrObjectsNormal++;
            }
        }
        logger.info("Nr objects extended: " + nrObjectsExtended + NEW_LINE);
        logger.info("Nr objects normal: " + nrObjectsNormal+ NEW_LINE);
        // logger.info("Nr of reversions: " + LayoutReverser.nrOfReversions+ NEW_LINE);
        logger.info("Nr of reversions: " + ActiveTransactionsRecord.nrOfReversions + NEW_LINE);
        logger.info("Number of tries = " + ActiveTransactionsRecord.nrOfTries + NEW_LINE);
        // logger.info("Number of Cleans = " + ActiveTransactionsRecord.nrOfCleans + NEW_LINE);
        //logger.info("Nr of aborted trxs: " + Transaction.nrOfAborts+ NEW_LINE);
        //Transaction.nrOfAborts = 0;
        logger.info(NEW_LINE);
    }
}