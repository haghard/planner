Planner
===================


Computation graph
===================

```scala
     
File           Parallel stage                                         Parallel stage
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