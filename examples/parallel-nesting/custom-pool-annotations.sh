#!/bin/sh

rm arraytree/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. arraytree/CustomPoolAnnotations.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:../../lib/asm-debug-all-4.0.jar jvstm.atomic.ProcessAtomicAnnotations arraytree
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:../../lib/asm-debug-all-4.0.jar jvstm.atomic.ProcessParNestAnnotations arraytree
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. arraytree.CustomPoolAnnotations loadTreeBalancedToBottom
rm arraytree/*.class
