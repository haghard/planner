Planner
===================


Computation graph
===================

```scala
     
File Source    Parallel Source                                              Parallel Flows
+----------+   +-----------+                                                +------------+
|csv_line0 |---|distribute |--+                                          +- |cuttingStock|----+
+----------+   +-----------+  |  Fan-in stage                            |  +------------+    |
+----------+   +-----------+  | +------+  +-----------------+  +-------+ |  +------------+    |   +-----+   +----------+
|csv_line1 |---|distribute |----|Merge |--|flatMapConcat    |--|Balance|----|cuttingStock |-------|Merge|---|Sink actor|
+----------+   +-----------+  | +------+  +-----------------+  +-------+ |  +------------+    |   +-----+   +----------+
                              |                                          |  +------------+    |
+----------+   +-----------+  |                                          +--|cuttingStock|----+
|csv_line2 |---|distribute |--+                                             +------------+
+----------+   +-----------+

```

Commands
=====================

```scala

check  ./csv/metal2pipes2.csv

plan  ./csv/metal2pipes2.csv --out json

plan  ./csv/metal2pipes2.csv --out excel
```


Links 


https://www.cakesolutions.net/teamblogs/solving-dynamic-programming-problems-using-functional-programming-part-1

https://www.geeksforgeeks.org/knapsack-problem/
https://www.geeksforgeeks.org/unbounded-knapsack-repetition-items-allowed/
https://developers.google.com/optimization/bin/knapsack#java
https://stackoverflow.com/questions/9559674/dp-algorithm-for-bounded-knapsack