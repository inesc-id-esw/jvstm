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
import jvstm.util.TransactionalOutputStream;
import jvstm.Atomic;

class WriteOutput {
    private static jvstm.VBox<Integer> box = new jvstm.VBox<Integer>(0);
    private static TransactionalOutputStream t = new TransactionalOutputStream(
            System.out);

    @Atomic
    private static void printIt() {
        t.writeln("Este valor: " + box.get());
        sleep(1000);
        t.writeln("É SEMPRE igual a: " + box.get());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            // empty
        }
    }

    @Atomic
    private static void incrementIt() {
        box.put(box.get() + 1);
    }

    public static void main(String[] args) {

        new Thread() {
            public void run() {
                for (int i = 0; i < 3; ++i) {
                    printIt();
                }
            }
        }.start();

        new Thread() {
            public void run() {
                for (int i = 0; i < 1; ++i) {
                    incrementIt();
                    WriteOutput.sleep(10);
                }
            }
        }.start();
    }
}
