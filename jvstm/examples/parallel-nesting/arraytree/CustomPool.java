package arraytree;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import jvstm.CommitException;
import jvstm.ParallelTask;
import jvstm.Transaction;
import jvstm.VBox;

/**
 * Unveils a nesting tree in which the leafs increment an array of VBoxes 
 * and create conflicts by overlapping with each other, both against direct 
 * siblings as well as against concurrent parallel nested transactions of 
 * different branches.
 * @author nmld
 *  
 */
public class CustomPool extends JVSTMTest<Long> {

    protected static final int[] POSSIBLE_DEPTH = { 0, 1, 2, 3, 4, 5, 6 };
    protected static final int[] POSSIBLE_LEAFS = { 1, 2, 4, 6, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 64, 72, 96 };
    protected static final int NUMBER_DEPTHS_VARS = POSSIBLE_DEPTH.length;
    protected static final int NUMBER_LEAFS_VARS = POSSIBLE_LEAFS.length;
    protected static final TreeLevel[][] TREE_COMPOSITIONS = new TreeLevel[NUMBER_DEPTHS_VARS][NUMBER_LEAFS_VARS];

    protected static TreeLevel makeNodes(int[] childs) {
	TreeLevel iter = new TreeLevel(childs.length - 1, childs[childs.length - 1], null);
	for (int i = childs.length - 2; i >= 0; i--) {
	    iter = new TreeLevel(i, childs[i], iter);
	}
	return iter;
    }

    protected static void loadTreeMostOnTop() {
	// Null everything first
	for (int d = 0; d < NUMBER_DEPTHS_VARS; d++) {
	    for (int l = 0; l < NUMBER_LEAFS_VARS; l++) {
		TREE_COMPOSITIONS[d][l] = null;
	    }
	}

	// DEPTH 0
	TREE_COMPOSITIONS[0][0] = makeNodes(new int[] { 0 });

	for (int k = 0; k < NUMBER_LEAFS_VARS; k++) {
	    int i = POSSIBLE_LEAFS[k];

	    // DEPTH 1
	    TREE_COMPOSITIONS[1][k] = makeNodes(new int[] { i, 0 });
	}
	TREE_COMPOSITIONS[1][0] = null;

	TREE_COMPOSITIONS[2][0] = null; // 1
	TREE_COMPOSITIONS[2][1] = null; // 2
	TREE_COMPOSITIONS[2][2] = makeNodes(new int[] { 2, 2, 0 }); // 4
	TREE_COMPOSITIONS[2][3] = makeNodes(new int[] { 3, 2, 0 }); // 6
	TREE_COMPOSITIONS[2][4] = makeNodes(new int[] { 4, 2, 0 }); // 8
	TREE_COMPOSITIONS[2][5] = makeNodes(new int[] { 6, 2, 0 }); // 12
	TREE_COMPOSITIONS[2][6] = makeNodes(new int[] { 8, 2, 0 }); // 16
	TREE_COMPOSITIONS[2][7] = makeNodes(new int[] { 10, 2, 0 }); // 20
	TREE_COMPOSITIONS[2][8] = makeNodes(new int[] { 12, 2, 0 }); // 24
	TREE_COMPOSITIONS[2][9] = makeNodes(new int[] { 14, 2, 0 }); // 28
	TREE_COMPOSITIONS[2][10] = makeNodes(new int[] { 16, 2, 0 }); // 32
	TREE_COMPOSITIONS[2][11] = makeNodes(new int[] { 18, 2, 0 }); // 36
	TREE_COMPOSITIONS[2][12] = makeNodes(new int[] { 20, 2, 0 }); // 40
	TREE_COMPOSITIONS[2][13] = makeNodes(new int[] { 22, 2, 0 }); // 44
	TREE_COMPOSITIONS[2][14] = makeNodes(new int[] { 24, 2, 0 }); // 48
	TREE_COMPOSITIONS[2][15] = makeNodes(new int[] { 32, 2, 0 }); // 64
	TREE_COMPOSITIONS[2][16] = makeNodes(new int[] { 36, 2, 0 }); // 72
	TREE_COMPOSITIONS[2][17] = makeNodes(new int[] { 48, 2, 0 }); // 96

	TREE_COMPOSITIONS[3][0] = null; // 1
	TREE_COMPOSITIONS[3][1] = null; // 2
	TREE_COMPOSITIONS[3][3] = null; // 4
	TREE_COMPOSITIONS[3][3] = null; // 6
	TREE_COMPOSITIONS[3][4] = makeNodes(new int[] { 2, 2, 2, 0 }); // 8
	TREE_COMPOSITIONS[3][5] = makeNodes(new int[] { 3, 2, 2, 0 }); // 12
	TREE_COMPOSITIONS[3][6] = makeNodes(new int[] { 4, 2, 2, 0 }); // 16
	TREE_COMPOSITIONS[3][7] = makeNodes(new int[] { 5, 2, 2, 0 }); // 20
	TREE_COMPOSITIONS[3][8] = makeNodes(new int[] { 6, 2, 2, 0 }); // 24
	TREE_COMPOSITIONS[3][9] = makeNodes(new int[] { 7, 2, 2, 0 }); // 28
	TREE_COMPOSITIONS[3][10] = makeNodes(new int[] { 8, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[3][11] = makeNodes(new int[] { 9, 2, 2, 0 }); // 36
	TREE_COMPOSITIONS[3][12] = makeNodes(new int[] { 10, 2, 2, 0 }); // 40
	TREE_COMPOSITIONS[3][13] = makeNodes(new int[] { 11, 2, 2, 0 }); // 44
	TREE_COMPOSITIONS[3][14] = makeNodes(new int[] { 12, 2, 2, 0 }); // 48
	TREE_COMPOSITIONS[3][15] = makeNodes(new int[] { 16, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[3][16] = makeNodes(new int[] { 18, 2, 2, 0 }); // 72
	TREE_COMPOSITIONS[3][17] = makeNodes(new int[] { 24, 2, 2, 0 }); // 96

	TREE_COMPOSITIONS[4][0] = null;
	TREE_COMPOSITIONS[4][1] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][4] = null;
	TREE_COMPOSITIONS[4][5] = null;
	TREE_COMPOSITIONS[4][6] = makeNodes(new int[] { 2, 2, 2, 2, 0 }); // 16
	TREE_COMPOSITIONS[4][7] = null; // 20
	TREE_COMPOSITIONS[4][8] = makeNodes(new int[] { 3, 2, 2, 2, 0 }); // 24
	TREE_COMPOSITIONS[4][9] = null; // 28
	TREE_COMPOSITIONS[4][10] = makeNodes(new int[] { 4, 2, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[4][11] = null; // 36
	TREE_COMPOSITIONS[4][12] = makeNodes(new int[] { 5, 2, 2, 2, 0 }); // 40
	TREE_COMPOSITIONS[4][13] = null; // 44
	TREE_COMPOSITIONS[4][14] = makeNodes(new int[] { 6, 2, 2, 2, 0 }); // 48
	TREE_COMPOSITIONS[4][15] = makeNodes(new int[] { 8, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[4][16] = makeNodes(new int[] { 9, 2, 2, 2, 0 }); // 72
	TREE_COMPOSITIONS[4][17] = makeNodes(new int[] { 12, 2, 2, 2, 0 }); // 96

	TREE_COMPOSITIONS[5][0] = null;
	TREE_COMPOSITIONS[5][1] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][4] = null;
	TREE_COMPOSITIONS[5][5] = null;
	TREE_COMPOSITIONS[5][6] = null;
	TREE_COMPOSITIONS[5][7] = null;
	TREE_COMPOSITIONS[5][8] = null;
	TREE_COMPOSITIONS[5][9] = null;
	TREE_COMPOSITIONS[5][10] = makeNodes(new int[] { 2, 2, 2, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[5][11] = null;
	TREE_COMPOSITIONS[5][12] = null;
	TREE_COMPOSITIONS[5][13] = null;
	TREE_COMPOSITIONS[5][14] = makeNodes(new int[] { 3, 2, 2, 2, 2, 0 }); // 48
	TREE_COMPOSITIONS[5][15] = makeNodes(new int[] { 4, 2, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[5][16] = null;
	TREE_COMPOSITIONS[5][17] = makeNodes(new int[] { 6, 2, 2, 2, 2, 0 }); // 96

	TREE_COMPOSITIONS[6][0] = null;
	TREE_COMPOSITIONS[6][1] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][4] = null;
	TREE_COMPOSITIONS[6][5] = null;
	TREE_COMPOSITIONS[6][6] = null;
	TREE_COMPOSITIONS[6][7] = null;
	TREE_COMPOSITIONS[6][8] = null;
	TREE_COMPOSITIONS[6][9] = null;
	TREE_COMPOSITIONS[6][10] = null;
	TREE_COMPOSITIONS[6][11] = null;
	TREE_COMPOSITIONS[6][12] = null;
	TREE_COMPOSITIONS[6][13] = null;
	TREE_COMPOSITIONS[6][14] = null;
	TREE_COMPOSITIONS[6][15] = makeNodes(new int[] { 2, 2, 2, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[6][16] = null;
	TREE_COMPOSITIONS[6][17] = makeNodes(new int[] { 3, 2, 2, 2, 2, 2, 0 }); // 96
    }

    protected static void loadTreeLeastOnTop() {
	// Null everything first
	for (int d = 0; d < NUMBER_DEPTHS_VARS; d++) {
	    for (int l = 0; l < NUMBER_LEAFS_VARS; l++) {
		TREE_COMPOSITIONS[d][l] = null;
	    }
	}

	// DEPTH 0
	TREE_COMPOSITIONS[0][0] = makeNodes(new int[] { 0 });

	for (int k = 0; k < NUMBER_LEAFS_VARS; k++) {
	    int i = POSSIBLE_LEAFS[k];

	    // DEPTH 1
	    TREE_COMPOSITIONS[1][k] = makeNodes(new int[] { i, 0 });
	}
	TREE_COMPOSITIONS[1][0] = null;

	TREE_COMPOSITIONS[2][0] = null; // 1
	TREE_COMPOSITIONS[2][1] = null; // 2
	TREE_COMPOSITIONS[2][2] = makeNodes(new int[] { 2, 2, 0 }); // 4
	TREE_COMPOSITIONS[2][3] = makeNodes(new int[] { 2, 3, 0 }); // 6
	TREE_COMPOSITIONS[2][4] = makeNodes(new int[] { 2, 4, 0 }); // 8
	TREE_COMPOSITIONS[2][5] = makeNodes(new int[] { 2, 6, 0 }); // 12
	TREE_COMPOSITIONS[2][6] = makeNodes(new int[] { 2, 8, 0 }); // 16
	TREE_COMPOSITIONS[2][7] = makeNodes(new int[] { 2, 10, 0 }); // 20
	TREE_COMPOSITIONS[2][8] = makeNodes(new int[] { 2, 12, 0 }); // 24
	TREE_COMPOSITIONS[2][9] = makeNodes(new int[] { 2, 14, 0 }); // 28
	TREE_COMPOSITIONS[2][10] = makeNodes(new int[] { 2, 16, 0 }); // 32
	TREE_COMPOSITIONS[2][11] = makeNodes(new int[] { 2, 18, 0 }); // 36
	TREE_COMPOSITIONS[2][12] = makeNodes(new int[] { 2, 20, 0 }); // 40
	TREE_COMPOSITIONS[2][13] = makeNodes(new int[] { 2, 22, 0 }); // 44
	TREE_COMPOSITIONS[2][14] = makeNodes(new int[] { 2, 24, 0 }); // 48
	TREE_COMPOSITIONS[2][15] = makeNodes(new int[] { 2, 32, 0 }); // 64
	TREE_COMPOSITIONS[2][16] = makeNodes(new int[] { 2, 36, 0 }); // 72
	TREE_COMPOSITIONS[2][17] = makeNodes(new int[] { 2, 48, 0 }); // 96

	TREE_COMPOSITIONS[3][0] = null; // 1
	TREE_COMPOSITIONS[3][1] = null; // 2
	TREE_COMPOSITIONS[3][3] = null; // 4
	TREE_COMPOSITIONS[3][3] = null; // 6
	TREE_COMPOSITIONS[3][4] = makeNodes(new int[] { 2, 2, 2, 0 }); // 8
	TREE_COMPOSITIONS[3][5] = makeNodes(new int[] { 2, 2, 3, 0 }); // 12
	TREE_COMPOSITIONS[3][6] = makeNodes(new int[] { 2, 2, 4, 0 }); // 16
	TREE_COMPOSITIONS[3][7] = makeNodes(new int[] { 2, 2, 5, 0 }); // 20
	TREE_COMPOSITIONS[3][8] = makeNodes(new int[] { 2, 2, 6, 0 }); // 24
	TREE_COMPOSITIONS[3][9] = makeNodes(new int[] { 2, 2, 7, 0 }); // 28
	TREE_COMPOSITIONS[3][10] = makeNodes(new int[] { 2, 2, 8, 0 }); // 32
	TREE_COMPOSITIONS[3][11] = makeNodes(new int[] { 2, 2, 9, 0 }); // 36
	TREE_COMPOSITIONS[3][12] = makeNodes(new int[] { 2, 2, 10, 0 }); // 40
	TREE_COMPOSITIONS[3][13] = makeNodes(new int[] { 2, 2, 11, 0 }); // 44
	TREE_COMPOSITIONS[3][14] = makeNodes(new int[] { 2, 2, 12, 0 }); // 48
	TREE_COMPOSITIONS[3][15] = makeNodes(new int[] { 2, 2, 16, 0 }); // 64
	TREE_COMPOSITIONS[3][16] = makeNodes(new int[] { 2, 2, 18, 0 }); // 72
	TREE_COMPOSITIONS[3][17] = makeNodes(new int[] { 2, 2, 24, 0 }); // 96

	TREE_COMPOSITIONS[4][0] = null;
	TREE_COMPOSITIONS[4][1] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][4] = null;
	TREE_COMPOSITIONS[4][5] = null;
	TREE_COMPOSITIONS[4][6] = makeNodes(new int[] { 2, 2, 2, 2, 0 }); // 16
	TREE_COMPOSITIONS[4][7] = null; // 20
	TREE_COMPOSITIONS[4][8] = makeNodes(new int[] { 2, 2, 2, 3, 0 }); // 24
	TREE_COMPOSITIONS[4][9] = null; // 28
	TREE_COMPOSITIONS[4][10] = makeNodes(new int[] { 2, 2, 2, 4, 0 }); // 32
	TREE_COMPOSITIONS[4][11] = null; // 36
	TREE_COMPOSITIONS[4][12] = makeNodes(new int[] { 2, 2, 2, 5, 0 }); // 40
	TREE_COMPOSITIONS[4][13] = null; // 44
	TREE_COMPOSITIONS[4][14] = makeNodes(new int[] { 2, 2, 2, 6, 0 }); // 48
	TREE_COMPOSITIONS[4][15] = makeNodes(new int[] { 2, 2, 2, 8, 0 }); // 64
	TREE_COMPOSITIONS[4][16] = makeNodes(new int[] { 2, 2, 2, 9, 0 }); // 72
	TREE_COMPOSITIONS[4][17] = makeNodes(new int[] { 2, 2, 2, 12, 0 }); // 128

	TREE_COMPOSITIONS[5][0] = null;
	TREE_COMPOSITIONS[5][1] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][4] = null;
	TREE_COMPOSITIONS[5][5] = null;
	TREE_COMPOSITIONS[5][6] = null;
	TREE_COMPOSITIONS[5][7] = null;
	TREE_COMPOSITIONS[5][8] = null;
	TREE_COMPOSITIONS[5][9] = null;
	TREE_COMPOSITIONS[5][10] = makeNodes(new int[] { 2, 2, 2, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[5][11] = null;
	TREE_COMPOSITIONS[5][12] = null;
	TREE_COMPOSITIONS[5][13] = null;
	TREE_COMPOSITIONS[5][14] = makeNodes(new int[] { 2, 2, 2, 2, 3, 0 }); // 48
	TREE_COMPOSITIONS[5][15] = makeNodes(new int[] { 2, 2, 2, 2, 4, 0 }); // 64
	TREE_COMPOSITIONS[5][16] = null;
	TREE_COMPOSITIONS[5][17] = makeNodes(new int[] { 2, 2, 2, 2, 6, 0 }); // 96

	TREE_COMPOSITIONS[6][0] = null;
	TREE_COMPOSITIONS[6][1] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][4] = null;
	TREE_COMPOSITIONS[6][5] = null;
	TREE_COMPOSITIONS[6][6] = null;
	TREE_COMPOSITIONS[6][7] = null;
	TREE_COMPOSITIONS[6][8] = null;
	TREE_COMPOSITIONS[6][9] = null;
	TREE_COMPOSITIONS[6][10] = null;
	TREE_COMPOSITIONS[6][11] = null;
	TREE_COMPOSITIONS[6][12] = null;
	TREE_COMPOSITIONS[6][13] = null;
	TREE_COMPOSITIONS[6][14] = null;
	TREE_COMPOSITIONS[6][15] = makeNodes(new int[] { 2, 2, 2, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[6][16] = null;
	TREE_COMPOSITIONS[6][17] = makeNodes(new int[] { 2, 2, 2, 2, 2, 3, 0 }); // 96
    }

    protected static void loadTreeBalancedToTop() {
	// Null everything first
	for (int d = 0; d < NUMBER_DEPTHS_VARS; d++) {
	    for (int l = 0; l < NUMBER_LEAFS_VARS; l++) {
		TREE_COMPOSITIONS[d][l] = null;
	    }
	}

	// DEPTH 0
	TREE_COMPOSITIONS[0][0] = makeNodes(new int[] { 0 });

	for (int k = 0; k < NUMBER_LEAFS_VARS; k++) {
	    int i = POSSIBLE_LEAFS[k];

	    // DEPTH 1
	    TREE_COMPOSITIONS[1][k] = makeNodes(new int[] { i, 0 });
	}
	TREE_COMPOSITIONS[1][0] = null;

	TREE_COMPOSITIONS[2][0] = null; // 1
	TREE_COMPOSITIONS[2][1] = null; // 2
	TREE_COMPOSITIONS[2][2] = makeNodes(new int[] { 2, 2, 0 }); // 4
	TREE_COMPOSITIONS[2][3] = makeNodes(new int[] { 3, 2, 0 }); // 6
	TREE_COMPOSITIONS[2][4] = makeNodes(new int[] { 4, 2, 0 }); // 8
	TREE_COMPOSITIONS[2][5] = makeNodes(new int[] { 4, 3, 0 }); // 12
	TREE_COMPOSITIONS[2][6] = makeNodes(new int[] { 4, 4, 0 }); // 16
	TREE_COMPOSITIONS[2][7] = makeNodes(new int[] { 5, 4, 0 }); // 20
	TREE_COMPOSITIONS[2][8] = makeNodes(new int[] { 6, 4, 0 }); // 24
	TREE_COMPOSITIONS[2][9] = makeNodes(new int[] { 7, 4, 0 }); // 28
	TREE_COMPOSITIONS[2][10] = makeNodes(new int[] { 8, 4, 0 }); // 32
	TREE_COMPOSITIONS[2][11] = makeNodes(new int[] { 9, 4, 0 }); // 36
	TREE_COMPOSITIONS[2][12] = makeNodes(new int[] { 8, 5, 0 }); // 40
	TREE_COMPOSITIONS[2][13] = makeNodes(new int[] { 11, 4, 0 }); // 44
	TREE_COMPOSITIONS[2][14] = makeNodes(new int[] { 8, 6, 0 }); // 48
	TREE_COMPOSITIONS[2][15] = makeNodes(new int[] { 8, 8, 0 }); // 64
	TREE_COMPOSITIONS[2][16] = makeNodes(new int[] { 9, 8, 0 }); // 72
	TREE_COMPOSITIONS[2][17] = makeNodes(new int[] { 12, 8, 0 }); // 96

	TREE_COMPOSITIONS[3][0] = null; // 1
	TREE_COMPOSITIONS[3][1] = null; // 2
	TREE_COMPOSITIONS[3][3] = null; // 4
	TREE_COMPOSITIONS[3][3] = null; // 6
	TREE_COMPOSITIONS[3][4] = makeNodes(new int[] { 2, 2, 2, 0 }); // 8
	TREE_COMPOSITIONS[3][5] = makeNodes(new int[] { 3, 2, 2, 0 }); // 12
	TREE_COMPOSITIONS[3][6] = makeNodes(new int[] { 4, 2, 2, 0 }); // 16
	TREE_COMPOSITIONS[3][7] = makeNodes(new int[] { 5, 2, 2, 0 }); // 20
	TREE_COMPOSITIONS[3][8] = makeNodes(new int[] { 4, 3, 2, 0 }); // 24
	TREE_COMPOSITIONS[3][9] = makeNodes(new int[] { 7, 2, 2, 0 }); // 28
	TREE_COMPOSITIONS[3][10] = makeNodes(new int[] { 4, 4, 2, 0 }); // 32
	TREE_COMPOSITIONS[3][11] = makeNodes(new int[] { 4, 3, 3, 0 }); // 36
	TREE_COMPOSITIONS[3][12] = makeNodes(new int[] { 5, 4, 2, 0 }); // 40
	TREE_COMPOSITIONS[3][13] = makeNodes(new int[] { 11, 2, 2, 0 }); // 44
	TREE_COMPOSITIONS[3][14] = makeNodes(new int[] { 4, 4, 3, 0 }); // 48
	TREE_COMPOSITIONS[3][15] = makeNodes(new int[] { 4, 4, 4, 0 }); // 64
	TREE_COMPOSITIONS[3][16] = makeNodes(new int[] { 6, 4, 3, 0 }); // 72
	TREE_COMPOSITIONS[3][17] = makeNodes(new int[] { 6, 4, 4, 0 }); // 96

	TREE_COMPOSITIONS[4][0] = null;
	TREE_COMPOSITIONS[4][1] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][4] = null;
	TREE_COMPOSITIONS[4][5] = null;
	TREE_COMPOSITIONS[4][6] = makeNodes(new int[] { 2, 2, 2, 2, 0 }); // 16
	TREE_COMPOSITIONS[4][7] = null; // 20
	TREE_COMPOSITIONS[4][8] = makeNodes(new int[] { 3, 2, 2, 2, 0 }); // 24
	TREE_COMPOSITIONS[4][9] = null; // 28
	TREE_COMPOSITIONS[4][10] = makeNodes(new int[] { 4, 2, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[4][11] = null; // 36
	TREE_COMPOSITIONS[4][12] = makeNodes(new int[] { 5, 2, 2, 2, 0 }); // 40
	TREE_COMPOSITIONS[4][13] = null; // 44
	TREE_COMPOSITIONS[4][14] = makeNodes(new int[] { 4, 3, 2, 2, 0 }); // 48
	TREE_COMPOSITIONS[4][15] = makeNodes(new int[] { 4, 4, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[4][16] = makeNodes(new int[] { 4, 3, 3, 2, 0 }); // 72
	TREE_COMPOSITIONS[4][17] = makeNodes(new int[] { 4, 4, 3, 2, 0 }); // 96

	TREE_COMPOSITIONS[5][0] = null;
	TREE_COMPOSITIONS[5][1] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][4] = null;
	TREE_COMPOSITIONS[5][5] = null;
	TREE_COMPOSITIONS[5][6] = null;
	TREE_COMPOSITIONS[5][7] = null;
	TREE_COMPOSITIONS[5][8] = null;
	TREE_COMPOSITIONS[5][9] = null;
	TREE_COMPOSITIONS[5][10] = makeNodes(new int[] { 2, 2, 2, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[5][11] = null;
	TREE_COMPOSITIONS[5][12] = null;
	TREE_COMPOSITIONS[5][13] = null;
	TREE_COMPOSITIONS[5][14] = makeNodes(new int[] { 3, 2, 2, 2, 2, 0 }); // 48
	TREE_COMPOSITIONS[5][15] = makeNodes(new int[] { 4, 2, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[5][16] = null;
	TREE_COMPOSITIONS[5][17] = makeNodes(new int[] { 4, 3, 2, 2, 2, 0 }); // 96

	TREE_COMPOSITIONS[6][0] = null;
	TREE_COMPOSITIONS[6][1] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][4] = null;
	TREE_COMPOSITIONS[6][5] = null;
	TREE_COMPOSITIONS[6][6] = null;
	TREE_COMPOSITIONS[6][7] = null;
	TREE_COMPOSITIONS[6][8] = null;
	TREE_COMPOSITIONS[6][9] = null;
	TREE_COMPOSITIONS[6][10] = null;
	TREE_COMPOSITIONS[6][11] = null;
	TREE_COMPOSITIONS[6][12] = null;
	TREE_COMPOSITIONS[6][13] = null;
	TREE_COMPOSITIONS[6][14] = null;
	TREE_COMPOSITIONS[6][15] = makeNodes(new int[] { 2, 2, 2, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[6][16] = null;
	TREE_COMPOSITIONS[6][17] = makeNodes(new int[] { 3, 2, 2, 2, 2, 2, 0 }); // 96
    }

    protected static void loadTreeBalancedToBottom() {
	// Null everything first
	for (int d = 0; d < NUMBER_DEPTHS_VARS; d++) {
	    for (int l = 0; l < NUMBER_LEAFS_VARS; l++) {
		TREE_COMPOSITIONS[d][l] = null;
	    }
	}

	// DEPTH 0
	TREE_COMPOSITIONS[0][0] = makeNodes(new int[] { 0 });

	for (int k = 0; k < NUMBER_LEAFS_VARS; k++) {
	    int i = POSSIBLE_LEAFS[k];

	    // DEPTH 1
	    TREE_COMPOSITIONS[1][k] = makeNodes(new int[] { i, 0 });
	}
	TREE_COMPOSITIONS[1][0] = null;

	TREE_COMPOSITIONS[2][0] = null; // 1
	TREE_COMPOSITIONS[2][1] = null; // 2
	TREE_COMPOSITIONS[2][2] = makeNodes(new int[] { 2, 2, 0 }); // 4
	TREE_COMPOSITIONS[2][3] = makeNodes(new int[] { 2, 3, 0 }); // 6
	TREE_COMPOSITIONS[2][4] = makeNodes(new int[] { 2, 4, 0 }); // 8
	TREE_COMPOSITIONS[2][5] = makeNodes(new int[] { 3, 4, 0 }); // 12
	TREE_COMPOSITIONS[2][6] = makeNodes(new int[] { 4, 4, 0 }); // 16
	TREE_COMPOSITIONS[2][7] = makeNodes(new int[] { 4, 5, 0 }); // 20
	TREE_COMPOSITIONS[2][8] = makeNodes(new int[] { 4, 6, 0 }); // 24
	TREE_COMPOSITIONS[2][9] = makeNodes(new int[] { 4, 7, 0 }); // 28
	TREE_COMPOSITIONS[2][10] = makeNodes(new int[] { 4, 8, 0 }); // 32
	TREE_COMPOSITIONS[2][11] = makeNodes(new int[] { 4, 9, 0 }); // 36
	TREE_COMPOSITIONS[2][12] = makeNodes(new int[] { 5, 8, 0 }); // 40
	TREE_COMPOSITIONS[2][13] = makeNodes(new int[] { 4, 11, 0 }); // 44
	TREE_COMPOSITIONS[2][14] = makeNodes(new int[] { 6, 8, 0 }); // 48
	TREE_COMPOSITIONS[2][15] = makeNodes(new int[] { 8, 8, 0 }); // 64
	TREE_COMPOSITIONS[2][16] = makeNodes(new int[] { 8, 9, 0 }); // 72
	TREE_COMPOSITIONS[2][17] = makeNodes(new int[] { 8, 12, 0 }); // 96

	TREE_COMPOSITIONS[3][0] = null; // 1
	TREE_COMPOSITIONS[3][1] = null; // 2
	TREE_COMPOSITIONS[3][3] = null; // 4
	TREE_COMPOSITIONS[3][3] = null; // 6
	TREE_COMPOSITIONS[3][4] = makeNodes(new int[] { 2, 2, 2, 0 }); // 8
	TREE_COMPOSITIONS[3][5] = makeNodes(new int[] { 2, 2, 3, 0 }); // 12
	TREE_COMPOSITIONS[3][6] = makeNodes(new int[] { 2, 2, 4, 0 }); // 16
	TREE_COMPOSITIONS[3][7] = makeNodes(new int[] { 2, 2, 5, 0 }); // 20
	TREE_COMPOSITIONS[3][8] = makeNodes(new int[] { 2, 3, 4, 0 }); // 24
	TREE_COMPOSITIONS[3][9] = makeNodes(new int[] { 2, 2, 7, 0 }); // 28
	TREE_COMPOSITIONS[3][10] = makeNodes(new int[] { 2, 4, 4, 0 }); // 32
	TREE_COMPOSITIONS[3][11] = makeNodes(new int[] { 3, 3, 4, 0 }); // 36
	TREE_COMPOSITIONS[3][12] = makeNodes(new int[] { 2, 4, 5, 0 }); // 40
	TREE_COMPOSITIONS[3][13] = makeNodes(new int[] { 2, 2, 11, 0 }); // 44
	TREE_COMPOSITIONS[3][14] = makeNodes(new int[] { 3, 4, 4, 0 }); // 48
	TREE_COMPOSITIONS[3][15] = makeNodes(new int[] { 4, 4, 4, 0 }); // 64
	TREE_COMPOSITIONS[3][16] = makeNodes(new int[] { 3, 4, 6, 0 }); // 72
	TREE_COMPOSITIONS[3][17] = makeNodes(new int[] { 4, 4, 6, 0 }); // 96

	TREE_COMPOSITIONS[4][0] = null;
	TREE_COMPOSITIONS[4][1] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][3] = null;
	TREE_COMPOSITIONS[4][4] = null;
	TREE_COMPOSITIONS[4][5] = null;
	TREE_COMPOSITIONS[4][6] = makeNodes(new int[] { 2, 2, 2, 2, 0 }); // 16
	TREE_COMPOSITIONS[4][7] = null; // 20
	TREE_COMPOSITIONS[4][8] = makeNodes(new int[] { 2, 2, 2, 3, 0 }); // 24
	TREE_COMPOSITIONS[4][9] = null; // 28
	TREE_COMPOSITIONS[4][10] = makeNodes(new int[] { 2, 2, 2, 4, 0 }); // 32
	TREE_COMPOSITIONS[4][11] = null; // 36
	TREE_COMPOSITIONS[4][12] = makeNodes(new int[] { 2, 2, 2, 5, 0 }); // 40
	TREE_COMPOSITIONS[4][13] = null; // 44
	TREE_COMPOSITIONS[4][14] = makeNodes(new int[] { 2, 2, 3, 4, 0 }); // 48
	TREE_COMPOSITIONS[4][15] = makeNodes(new int[] { 2, 2, 4, 4, 0 }); // 64
	TREE_COMPOSITIONS[4][16] = makeNodes(new int[] { 2, 3, 3, 4, 0 }); // 72
	TREE_COMPOSITIONS[4][17] = makeNodes(new int[] { 2, 3, 4, 4, 0 }); // 96

	TREE_COMPOSITIONS[5][0] = null;
	TREE_COMPOSITIONS[5][1] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][3] = null;
	TREE_COMPOSITIONS[5][4] = null;
	TREE_COMPOSITIONS[5][5] = null;
	TREE_COMPOSITIONS[5][6] = null;
	TREE_COMPOSITIONS[5][7] = null;
	TREE_COMPOSITIONS[5][8] = null;
	TREE_COMPOSITIONS[5][9] = null;
	TREE_COMPOSITIONS[5][10] = makeNodes(new int[] { 2, 2, 2, 2, 2, 0 }); // 32
	TREE_COMPOSITIONS[5][11] = null;
	TREE_COMPOSITIONS[5][12] = null;
	TREE_COMPOSITIONS[5][13] = null;
	TREE_COMPOSITIONS[5][14] = makeNodes(new int[] { 2, 2, 2, 2, 3, 0 }); // 48
	TREE_COMPOSITIONS[5][15] = makeNodes(new int[] { 2, 2, 2, 2, 4, 0 }); // 64
	TREE_COMPOSITIONS[5][16] = null;
	TREE_COMPOSITIONS[5][17] = makeNodes(new int[] { 2, 2, 2, 3, 4, 0 }); // 96

	TREE_COMPOSITIONS[6][0] = null;
	TREE_COMPOSITIONS[6][1] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][3] = null;
	TREE_COMPOSITIONS[6][4] = null;
	TREE_COMPOSITIONS[6][5] = null;
	TREE_COMPOSITIONS[6][6] = null;
	TREE_COMPOSITIONS[6][7] = null;
	TREE_COMPOSITIONS[6][8] = null;
	TREE_COMPOSITIONS[6][9] = null;
	TREE_COMPOSITIONS[6][10] = null;
	TREE_COMPOSITIONS[6][11] = null;
	TREE_COMPOSITIONS[6][12] = null;
	TREE_COMPOSITIONS[6][13] = null;
	TREE_COMPOSITIONS[6][14] = null;
	TREE_COMPOSITIONS[6][15] = makeNodes(new int[] { 2, 2, 2, 2, 2, 2, 0 }); // 64
	TREE_COMPOSITIONS[6][16] = null;
	TREE_COMPOSITIONS[6][17] = makeNodes(new int[] { 2, 2, 2, 2, 2, 3, 0 }); // 96
    }

    static {
	// loadTreeMostOnTop();
	// loadTreeLeastOnTop();
	// loadTreeBalancedToTop();
	// loadTreeBalancedToBottom();
    }

    public CustomPool(String balance) {
	try {
	    Class<CustomPool> clazz = (Class<CustomPool>) this.getClass();
	    Method mthd = clazz.getDeclaredMethod(balance);
	    mthd.invoke(null);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    protected static class TreeLevel {
	public final int level;
	public final int numberChildren;
	public final TreeLevel nextLevel; // bellow

	public TreeLevel(int level, int numberChildren, TreeLevel nextLevel) {
	    this.level = level;
	    this.numberChildren = numberChildren;
	    this.nextLevel = nextLevel;
	}
    }

    public long elapsedTimes[];
    public static int DEPTH;
    public static int LEAFS;
    public int ARRAY_SIZE;
    public final int NUMBER_INCS = 2;
    public final int ATTEMPTS = 1;
    public final int NUMBER_CONFLICTERS = 8;
    public VBox<Long>[] counters;
    public ExecutorService threadPool = Executors.newFixedThreadPool(128);
    public TopWorker[] topLevels;
    public TreeLevel rootNode;

    public class TopWorker extends Thread {

	private final int id;

	public TopWorker() {
	    this.id = -1;
	}

	public TopWorker(int id) {
	    this.id = id;
	}

	public int getNumber() {
	    return this.id;
	}

	private final ThreadLocal<Random> localRandom = new ThreadLocal<Random>() {
	    @Override
	    protected Random initialValue() {
		return new Random();
	    }
	};

	private void compute() {
	    long l1 = localRandom.get().nextLong();
	    long l2 = localRandom.get().nextLong();
	    for (long i = 0L; i < 100000L; i++) {
		if (i % 2 == 0) {
		    l1 ^= l2;
		} else {
		    l2 ^= l1;
		}
	    }
	}

	@Override
	public void run() {
	    try {
		while (true) {
		    Transaction tx = Transaction.begin();
		    try {
			execute();
			tx.commit();
			tx = null;
			return;
		    } catch (CommitException ce) {
			tx.abort();
			tx = null;
		    } finally {
			if (tx != null) {
			    tx.abort();
			}
		    }
		}
	    } catch (Throwable e) {
		e.printStackTrace();
	    }
	}

	public void execute() {
	    if (id == -1) {
		List<ParallelTask<Void>> tasks = new ArrayList<ParallelTask<Void>>();
		int index = 0;
		int incr = LEAFS / rootNode.numberChildren;
		for (int i = 0; i < rootNode.numberChildren; i++) {
		    tasks.add(new ForkWork(index, incr, rootNode.nextLevel));
		    index += incr;
		}
		Transaction.current().manageNestedParallelTxs(tasks, threadPool);
	    } else {
		int SLICE = (ARRAY_SIZE / LEAFS);
		int base = id * SLICE;
		int top = (id + 1) * SLICE;

		for (int i = 0; i < NUMBER_INCS - 1; i++) {
		    for (int arr_idx = base; arr_idx < top; arr_idx++) {
			VBox<Long> ctr = counters[arr_idx];
			if ((arr_idx % 100) == 0) {
			    compute();
			}
			ctr.put(ctr.get() + 1L);
		    }
		}

		int CONFLICTS = 0;
		if ((id + 1) <= NUMBER_CONFLICTERS) {
		    CONFLICTS = 1;
		}
		if (base == 0 && top == counters.length) {
		    // we change nothing
		} else if (base == 0) {
		    // move to right, hole on left
		    base += CONFLICTS;
		    top += CONFLICTS;
		} else if (top == counters.length) {
		    // move to left, hole on right
		    top -= CONFLICTS;
		    base -= CONFLICTS;
		} else {
		    // move right , hole on left
		    base += CONFLICTS;
		    top += CONFLICTS;
		}

		for (int arr_idx = base; arr_idx < top; arr_idx++) {
		    if ((arr_idx % 100) == 0) {
			compute();
		    }
		    VBox<Long> ctr = counters[arr_idx];
		    ctr.put(ctr.get() + 1L);
		}
	    }
	}


    }

    public class ForkWork extends ParallelTask<Void> {

	private final int id;
	private final int idsToCover;
	private final TreeLevel level;

	public ForkWork(int id, int idsToCover, TreeLevel level) {
	    super();
	    this.id = id;
	    this.idsToCover = idsToCover;
	    this.level = level;
	}

	private final ThreadLocal<Random> localRandom = new ThreadLocal<Random>() {
	    @Override
	    protected Random initialValue() {
		return new Random();
	    }
	};

	private void compute() {
	    long l1 = localRandom.get().nextLong();
	    long l2 = localRandom.get().nextLong();
	    for (long i = 0L; i < 100000L; i++) {
		if (i % 2 == 0) {
		    l1 ^= l2;
		} else {
		    l2 ^= l1;
		}
	    }
	}

	@Override
	public Void execute() throws Throwable {
	    int SLICE = (ARRAY_SIZE / LEAFS);
	    int base = id * SLICE;
	    int top = (id + 1) * SLICE;

	    int CONFLICTS = 0;
	    if ((id + 1) <= NUMBER_CONFLICTERS) {
		CONFLICTS = 1;
	    }
	    if (base == 0 && top == counters.length) {
		// we change nothing
	    } else if (base == 0) {
		// move to right, hole on left
		base += CONFLICTS;
		top += CONFLICTS;
	    }


	    if (level.nextLevel == null) {
		SLICE = (ARRAY_SIZE / LEAFS);
		base = id * SLICE;
		top = (id + 1) * SLICE;

		for (int i = 0; i < NUMBER_INCS - 1; i++) {
		    for (int arr_idx = base; arr_idx < top; arr_idx++) {
			VBox<Long> ctr = counters[arr_idx];
			if ((arr_idx % 100) == 0) {
			    compute();
			}
			ctr.put(ctr.get() + 1L);
		    }
		}

		CONFLICTS = 0;
		if ((id + 1) <= NUMBER_CONFLICTERS) {
		    CONFLICTS = 1;
		}
		if (base == 0 && top == counters.length) {
		    // we change nothing
		} else if (base == 0) {
		    // move to right, hole on left
		    base += CONFLICTS;
		    top += CONFLICTS;
		} else if (top == counters.length) {
		    // move to left, hole on right
		    top -= CONFLICTS;
		    base -= CONFLICTS;
		} else {
		    // move right , hole on left
		    base += CONFLICTS;
		    top += CONFLICTS;
		}

		for (int arr_idx = base; arr_idx < top; arr_idx++) {
		    if ((arr_idx % 100) == 0) {
			compute();
		    }
		    VBox<Long> ctr = counters[arr_idx];
		    ctr.put(ctr.get() + 1L);
		}
	    } else {
		int incr = idsToCover / level.numberChildren;
		List<ParallelTask<Void>> tasks = new ArrayList<ParallelTask<Void>>();
		int index = id;
		for (int i = 0; i < level.numberChildren; i++) {
		    tasks.add(new ForkWork(index, incr, level.nextLevel));
		    index += incr;
		}

		Transaction.current().manageNestedParallelTxs(tasks, threadPool);
	    }

	    return null;
	}
    }

    public static void main(String[] args) throws Exception {
	if (args.length != 1) {
	    System.err.println("Provide the tree balance type. For instance: loadTreeBalancedToBottom");
	    System.exit(1);
	}
	new CustomPool(args[0]).test();
    }

    @Override
    public void before() throws Exception {
	for (int i = 0; i < counters.length; i++) {
	    counters[i] = new VBox<Long>(0L);
	}
	if (rootNode.numberChildren == 0) {
	    topLevels = new TopWorker[LEAFS];
	    for (int i = 0; i < topLevels.length; i++) {
		topLevels[i] = new TopWorker(i);
	    }
	} else {
	    topLevels = new TopWorker[1];
	    super.createTopLevels(topLevels, TopWorker.class);
	}
	Thread.sleep(10);
	Runtime.getRuntime().gc();
	Thread.sleep(10);
    }

    @Override
    public void execute() throws Exception {
	super.startTopLevels(topLevels);
	super.joinTopLevels(topLevels);
    }

    @Override
    public void after() {
	threadPool.shutdown();
    }

    @Override
    public Long obtainResult() {
	Long sum = 0L;
	for (VBox<Long> ctr : counters) {
	    sum += ctr.get();
	}
	return sum;
    }

    @Override
    public Long expectedValue() {
	return new Long(ARRAY_SIZE * NUMBER_INCS);
    }

    @Override
    public boolean test() throws Exception {
	StringBuilder output = new StringBuilder();
	StringBuilder profiling = new StringBuilder();
	boolean result = true;
	for (int threadIdx = 0; threadIdx < NUMBER_LEAFS_VARS; threadIdx++) {
	    int threads = POSSIBLE_LEAFS[threadIdx];
	    output.append("\n" + threads);
	    profiling.append("\n" + threads);
	    LEAFS = threads;
	    for (int depthIdx = 0; depthIdx < NUMBER_DEPTHS_VARS; depthIdx++) {
		int depth = POSSIBLE_DEPTH[depthIdx];
		rootNode = TREE_COMPOSITIONS[depthIdx][threadIdx];
		DEPTH = depth;
		System.err.println("######\nLeafs " + LEAFS + " Depth " + DEPTH);
		if (rootNode == null) {
		    output.append(" 0");
		    profiling.append(" 0");
		    System.err.println("\tImpossible");
		    continue;
		}
		elapsedTimes = new long[LEAFS];
		ARRAY_SIZE = 221760;
		long timeTakenAttempts = 0L;
		long[] tentativeTimes = new long[ATTEMPTS];
		for (int i = 0; i < ATTEMPTS; i++) {
		    counters = new VBox[ARRAY_SIZE];
		    threadPool = Executors.newFixedThreadPool(128);
		    Thread.sleep(10);
		    Runtime.getRuntime().gc();
		    Thread.sleep(10);
		    try {
			result = super.test();
			timeTakenAttempts += super.lastTime;
			tentativeTimes[i] = super.lastTime;
			long avg = 0;
			for (int k = 0; k < elapsedTimes.length; k++) {
			    avg += elapsedTimes[k];
			}
			avg = avg / elapsedTimes.length;

			if (!result) {
			    System.err.println("### FAILED ! " + LEAFS + " leafs; " + DEPTH + " depth" + " ###");
			    return result;
			}
		    } catch (ArithmeticException e) {
			output.append(" 0");
			profiling.append(" 0");
			System.err.println("\tImpossible");
			break;
		    }
		}
		long tentativeAvg = timeTakenAttempts / ATTEMPTS;
		long fixedAvg = 0L;
		int attemptsUsed = 0;
		for (int k = 0; k < ATTEMPTS; k++) {
		    long difference = Math.abs(tentativeAvg - tentativeTimes[k]);
		    if (((difference + 0.0) / (tentativeAvg + 0.0)) < 0.5) {
			fixedAvg += tentativeTimes[k];
			attemptsUsed++;
		    }
		}
		if (fixedAvg == 0) {
		    fixedAvg = tentativeAvg;
		} else {
		    fixedAvg = fixedAvg / attemptsUsed;
		}
		output.append(" " + fixedAvg);
		System.err.println("Old avg: " + tentativeAvg + " fixed " + fixedAvg);
	    }
	}
	System.out.println("\n" + output.toString() + "\n");
	return result;
    }

}
