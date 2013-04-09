
import java.util.ArrayList;
import jvstm.*;

public class SudokuSolver {

    private Cell[][] cells = new Cell[9][9];

    SudokuSolver() {
        initializeCells();
    }

    void initializeCells() {
        // create all cells
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                cells[r][c] = new Cell(r,c);
            }
        }
        
        // connect neighbors
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                connectNeighborsFor(r, c);
            }
        }
    }

    void connectNeighborsFor(int row, int col) {
        int regionRow = row / 3;
        int regionCol = col / 3;

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (! ((row == r) && (col == c))) {
                    if ((row == r) || (col == c) || 
                        ((regionRow == (r / 3)) && (regionCol == (c / 3)))) {
                        cells[row][col].addNeighbor(cells[r][c]);
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
                    cell.setNum(Character.digit(given, 10));
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
        Cell mostConstrained = findMostConstrainedCell();
        if (mostConstrained.countPossibleValues() > 1) {
            tryValuesFor(mostConstrained);
        }
    }

    void tryValuesFor(Cell cell) {
        int[] values = cell.getPossibleValues();
        int pos = 0;
        while (pos < values.length) {
            //System.out.println("Will try value " + values[pos] + " for cell at (" + cell.row + ", " + cell.col + ")");
            Transaction.begin();
            try {
                cell.setNum(values[pos]);
                solve();
                Transaction.commit();
                //System.out.println("Succeed");
                return;
            } catch (Fail f) {
                //System.out.println("Failed");
                Transaction.abort();
                pos++;
            }
        }
        throw new Fail();
    }

    Cell findMostConstrainedCell() {
        Cell best = cells[0][0];
        int lessPossibilities = 10;

        for (Cell[] row : cells) {
            for (Cell c : row) {
                int possibilities = c.countPossibleValues();
                
                if ((possibilities > 1) && (possibilities < lessPossibilities)) {
                    best = c;
                    lessPossibilities = possibilities;
                }
            }
        }
        return best;
    }

    public static void main(String[] args) {
        Transaction.begin();
        SudokuSolver solver = new SudokuSolver();
        solver.setGivens(args[0]);
        solver.solve();
        solver.print();
        Transaction.commit();
    }


    static class Cell {
        private static final int ALL = (1 << 9) - 1;
        private int row, col;

        private VBox<Integer> nums = new VBox<Integer>();

        private ArrayList<Cell> neighbors = new ArrayList<Cell>();

        Cell(int row, int col) {
            this.row = row;
            this.col = col;
            nums.put(ALL);
        }

        void addNeighbor(Cell neighbor) {
            //System.out.println("Adding cell at (" + neighbor.row + ", " + neighbor.col + ") as neighbor of (" + this.row + ", " + this.col + ")");
            neighbors.add(neighbor);
        }

        void remove(int num) {
            int oldValue = this.nums.get();
            int newValue = (oldValue & (~ (1 << (num - 1))));

            //System.out.println("Removing " + num + " from (" + this.row + ", " + this.col + "): " + oldValue + " -> " + newValue);

            if (oldValue == newValue) {
                return;
            }

            if (newValue == 0) {
                throw new Fail();
            }

            setValue(newValue);
        }

        void setNum(int num) {
            setValue(1 << (num - 1));
        }

        void setValue(int newValue) {
            this.nums.put(newValue);

            if (Integer.bitCount(newValue) == 1) {
                for (Cell c : neighbors) {
                    c.remove(Integer.numberOfTrailingZeros(newValue) + 1);
                }
            }
        }

        int[] getPossibleValues() {
            int[] vals = new int[countPossibleValues()];

            int value = this.nums.get();
            int pos = 0;

            int num = 1;
            while (value != 0) {
                if ((value & 1) != 0) {
                    vals[pos++] = num;
                }
                num++;
                value = (value >> 1);
            }
            return vals;
        }

        int countPossibleValues() {
            return Integer.bitCount(this.nums.get());
        }

        int getCurrentSingleValue() {
            return Integer.numberOfTrailingZeros(this.nums.get()) + 1;
        }

        void print() {
            int value = this.nums.get();
            if (Integer.bitCount(value) == 1) {
                System.out.print(" " + getCurrentSingleValue() + " ");
            } else {
                System.out.print(" ? ");
            }
        }
    }

    static class Fail extends RuntimeException {}
}
