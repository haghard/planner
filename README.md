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

Httpie
=====================

```scala

http POST http://127.0.0.1:8001/orders < ./csv/metal2pipes3.csv Accept:application/json --stream
```