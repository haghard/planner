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
|csv_line1 |---|distribute |----|Merge |--|flatten/mapConcat|--|Balance|----|cuttingStock |-------|Merge|---|Sink actor|
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