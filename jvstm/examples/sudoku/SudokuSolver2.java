
import jvstm.*;
import pt.ist.esw.atomicannotation.Atomic;

public class SudokuSolver2 {

    private Cell[][] cells = new Cell[9][9];

    SudokuSolver2() {
        // create all cells
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                cells[r][c] = new Cell(r, c);
            }
        }
    }

    void removeChoiceFromNeighbors(int row, int col, int choice) {
        int regionRow = row / 3;
        int regionCol = col / 3;

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (! ((row == r) && (col == c))) {
                    if ((row == r) || (col == c) || 
                        ((regionRow == (r / 3)) && (regionCol == (c / 3)))) {
                        cells[r][c].removeChoice(choice);
                    }
                }
            }
        }
    }

    void setGivens(String givens) {
        int pos = 0;
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                char given = givens.charAt(pos);
                pos++;

                if (given != '?') {
                    cell.setValue(Character.digit(given, 10));
                }
            }
        }
    }

    void print() {
        for (Cell[] row : cells) {
            System.out.println();
            for (Cell cell : row) {
                cell.print();
            }
            System.out.println();
        }
    }

    void solve() {
        Cell unsolvedCell = findUnsolvedCell();
        if (unsolvedCell != null) {
            tryValuesFor(unsolvedCell);
        }
    }

    void tryValuesFor(Cell cell) {
        int first = cell.getFirstChoice();

	try {
	    tryOneValue(cell, first);
	} catch (Fail f) {
	    cell.removeChoice(first);
	    tryValuesFor(cell);
	}
    }
    
    @Atomic void tryOneValue(Cell cell, int num) {
	cell.setValue(num);
	solve();
    }


    Cell findUnsolvedCell() {
        for (Cell[] row : cells) {
            for (Cell c : row) {
                if (c.countChoices() > 1) {
		    return c;
		}
	    }
	}

        return null;
    }

    public static void main(String[] args) {
        SudokuSolver2 solver = new SudokuSolver2();
	long start = System.currentTimeMillis();
        solver.setGivens(args[0]);
        solver.solve();
	System.out.println("Solved in " + (System.currentTimeMillis() - start) + "ms");
        solver.print();
    }

    static class Choice extends VBox<Boolean> {
	Choice() {
	    super(true);
	}
    }

    class Cell {
	private final int row;
	private final int col;

        private Choice[] choices = new Choice[9];
	private VBox<Integer> numChoices = new VBox<Integer>(9);

        Cell(int row, int col) {
	    this.row = row;
	    this.col = col;

	    for (int i = 0; i < 9; i++) {
		choices[i] = new Choice();
	    }
        }

	boolean hasChoice(int num) {
	    return choices[num-1].get();
	}

	void setChoice(int num, boolean possible) {
	    choices[num-1].put(possible);
	}

	void removeChoice(int num) {
	    if (hasChoice(num)) {
		setChoice(num, false);
		numChoices.put(numChoices.get() - 1);
		checkChoices();
	    }
	}

	void checkChoices() {
	    int numChoices = countChoices();
	    if (numChoices == 0) {
		throw new Fail();
	    } else if (numChoices == 1) {
		removeChoiceFromNeighbors(row, col, getFirstChoice());
	    }
	}

        void setValue(int newValue) {
	    if (! hasChoice(newValue)) {
		throw new Fail();
	    }

	    for (int i = 1; i <= choices.length; i++) {
		setChoice(i, i == newValue);
	    }
	    numChoices.put(1);
	    checkChoices();
        }

        int getFirstChoice() {
	    for (int i = 1; i <= choices.length; i++) {
		if (hasChoice(i)) {
		    return i;
		}
	    }
	    return 0;
        }

        int countChoices() {
	    return numChoices.get();
        }

        void print() {
            if (countChoices() == 1) {
                System.out.print(" " + getFirstChoice() + " ");
            } else {
                System.out.print(" ? ");
            }
        }
    }

    static class Fail extends RuntimeException {}
}
