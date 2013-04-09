/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm.util;

import jvstm.VArray;

/** Multidimensional versioned array implemented using a (linear) VArray. **/
/* Math:
 *      Creation:
 *      new VMultiArray(A, B, C, ..., Z) => new VArray(A*B*C*...*Z)
 *
 *      Access:
 *      VMultiArray.get(a, b, c, ..., z) => VArray.get(a + b*(A) + c*(A*B) + d*(A*B*C) + ... + z*(A*B*C*...*Y)
 *      with 0 <= a < A, 0 <= b < B, ... 0 <= z < Z
 */
public class VMultiArray<E> {

    public final VArray<E> array;
    public final int[] dimensions;

    public VMultiArray(int ... dimensions) {
        if (dimensions.length <= 1) {
            throw new UnsupportedOperationException("Cannot create multidimensional array with less than two dimensions");
        }

        int size = 1;
        for (int i : dimensions) {
            if (i <= 0) throw new NegativeArraySizeException();
            size *= i;
        }

        this.array = new VArray<E>(size);
        this.dimensions = dimensions.clone();   // Keep our copy private, to avoid unpleasantries
    }

    private final int coordinatesToIndex(int ... coordinates) {
        if (coordinates.length != dimensions.length) {
            throw new ArrayIndexOutOfBoundsException("Number of coordinates does not match number of dimensions");
        }

        int arrayIndex = 0;
        int currentDim = 0;
        int partialDim = 1;     // Length of previous dimensions. Could be precalculated, but we will have to read their
                                // sizes for bounds checking anyway, so we trade having to keep some other array for
                                // a couple more cpu instructions.

        for (; currentDim < dimensions.length; currentDim++) {
            int coordIndex = coordinates[currentDim];
            int dimSize = dimensions[currentDim];

            if (coordIndex < 0 || coordIndex >= dimSize) throw new ArrayIndexOutOfBoundsException();

            arrayIndex += coordIndex * partialDim;
            partialDim *= dimSize;
        }

        return arrayIndex;
    }

    public E get(int ... coordinates) {
        return array.get(coordinatesToIndex(coordinates));
    }

    // Argument order inverted due to programming language limitations, sorry
    public void put(E newE, int ... coordinates) {
        array.put(coordinatesToIndex(coordinates), newE);
    }

}
