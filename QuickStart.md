---
layout: page
---

# Quick start

The distinctive element of the JVSTM model is the use of versioned boxes to hold the 
mutable state of a concurrent program. Versioned boxes can be seen as a replacement 
for memory locations or transactional variables.

JVSTM is implemented as a pure-Java library that provides only two visible interfaces
for the programmers that use it: `VBox` and `Transaction`.
Each instance of the `VBox` is a versioned box, capable of holding a history of values.

To transactify a program with the JVSTM you need to replace the definition of all 
transactional locations by versioned boxes. In this case, you can do it automatically 
with the support of the Deuce STM framework. Although the original distribution of 
Deuce does not provide support to store metadata in-place (such as the versions history), 
we made an adaptation of Deuce that supports this feature.

