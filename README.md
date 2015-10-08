Planner
===================

Computation graph
===================

```scala

Request                                                                                     Parallel stage
+----------+   +---------------+  +-----+                                                   +------------+
|order0    |---|Stateful reader|--|queue|-+  Parallel stage                            +----|cuttingStock|----+
+----------+   +---------------+  +-----+ |  +----------+      Fan-in stage            |    +------------+    |
+----------+                              |--|distribute|---+  +----------+  +-----+   |    +------------+    |  +------------+   +-------+
|order1    |                              |  +----------+   |  |foldMonoid|--|queue|--------|cuttingStock|-------|monoidMapper|---|convert|
+----------+                              |  +----------+   +--+----------+  +-----+   |    +------------+    |  +------------+   +-------+
                                          |--|distribute|---+                          |    +------------+    |
+----------+                              |  +----------+   |                          +----|cuttingStock|----+
|order2    |                              |  +----------+   |                               +------------+
+----------+                              |--|distribute|---+
                                             +----------+
```


Commands
======================

```scala

http POST http://127.0.0.1:9001/orders < ./csv/metal2pipes3.csv Accept:application/json --stream
```