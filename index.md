---
layout: page
---

# Welcome to the JVSTM project

The JVSTM (Java Versioned STM) is a Java library implementing 
our approach to STM (Software Transactional Memory), which 
introduces the concept of versioned boxes.

The Java Versioned Software Transactional Memory (JVSTM) is a 
pure Java library implementing an STM [(1)](#cachopo-phd-2007). 
JVSTM introduces the concept of versioned boxes [(2)](#cachopo-SCP-2006),
which are transactional locations that may be read and written during 
transactions, much in the same way of other STMs, except that 
they keep the history of values written to them by any committed
transaction.

Since version 2 the JVSTM implementation is entirely non-blocking
[(3)](#fernandes-ppopp-2011).

[<a id="cachopo-phd-2007">1</a>] João Cachopo: **Development of Rich Domain
Models with Atomic Actions**. Ph.D. thesis, Technical University of Lisbon
(2007)

[<a id="cachopo-SCP-2006">2</a>] João Cachopo, António Rito-Silva: **Versioned
boxes as the basis for memory transactions**. Science of Computer Programming
63(2), 172-185 (2006)

[<a id="fernandes-ppopp-2011">3</a>] Sérgio Miguel Fernandes, João Cachopo:
**Lock-free and scalable multi-version Software Transactional Memory**. 16th
ACM SIGPLAN Annual Symposium on Principles and Practice of Parallel
Programming, 179--188 (2011)
