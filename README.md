Planner
===================

Computation graph
===================

```scala

Request         Parallel stage                                         Parallel stage
+----------+   +-----------+                                          +------------+
|csv_line0 |---|distribute |--+                                  +----|cuttingStock|----+
+----------+   +-----------+  |  Fan-in stage  Back pres prim    |    +------------+    |
+----------+   +-----------+  |  +----------+  +-------------+   |    +------------+    |  +------------+   +-------+
|csv_line1 |---|distribute |-----|foldMonoid|--|bounded queue|--------|cuttingStock|-------|monoidMapper|---|convert|
+----------+   +-----------+  |  +----------+  +-------------+   |    +------------+    |  +------------+   +-------+
                              |                                  |    +------------+    |
+----------+   +-----------+  |                                  +----|cuttingStock|----+
|csv_line2 |---|distribute |--+                                       +------------+
+----------+   +-----------+

```


Commands
======================

```scala

http POST http://127.0.0.1:9001/orders < ./csv/metal2pipes3.csv Accept:application/json --stream
```