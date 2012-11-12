---
layout: page
title: JVSTM
tagline: Java Versioned STM
---
{% include JB/setup %}

The JVSTM (Java Versioned STM) is a Java library implementing our approach to STM (Software Transactional Memory), which introduces the concept of versioned boxes.

The Java Versioned Software Transactional Memory (JVSTM) is a pure Java library implementing an STM \[[1](#ref1)\]. JVSTM introduces the concept of versioned boxes \[[2](#ref2)\], which are transactional locations that may be read and written during
transactions, much in the same way of other STMs, except that they keep the history of values written to them by any committed transaction.

\[<a id="ref1">1</a>\] Cachopo, J.: Development of Rich Domain Models with Atomic Actions. Ph.D. thesis, Technical University of Lisbon (2007)

\[<a id="ref2">2</a>\] Cachopo, J., Rito-Silva, A.: Versioned boxes as the basis for memory transactions.
Science of Computer Programming 63(2), 172-185 (2006)
