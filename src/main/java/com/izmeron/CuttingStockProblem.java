package com.izmeron;

import java.util.*;

public class CuttingStockProblem {
  private int[] block;
  private int[] qty;
  private int[] comb;
  private int[] tempCombination;
  private int[] limit;
  private int max;
  private int total;
  private int counter = 0;
  private int waste = 0;
  private int count = 0;
  private List<Map<Integer, Integer>> mapList = new ArrayList<>();
  private List<Integer> store = new ArrayList<>();

  public boolean hasMoreCombinations() {
    return this.count < this.counter;
  }

  public Map<Integer, Integer> nextBatch() {
    Map<Integer, Integer> map = mapList.get(count);
    ++count;
    return map;
  }

  public CuttingStockProblem(int max, int[] block, int[] quantity) throws Exception {
    for(int i = 0; i < block.length; ++i) {
      if(block[i] > max) {
        throw new Exception("Invalid length");
      }
    }

    if(block.length != quantity.length) {
      throw new Exception("Invalid length");
    } else {
      total = block.length;
      this.max = max;
      this.block = block;
      qty = quantity;
      initialize();
    }
  }

  private void initialize() {
    store = new ArrayList<>();
    waste = 0;
    counter = 0;
    sort();
    calculate(store);
  }

  private void sort() {
    boolean swap;
    do {
      swap = false;

      for(int j = 0; j < total - 1; ++j) {
        if(block[j + 1] > block[j]) {
          int tmp = block[j];
          block[j] = block[j + 1];
          block[j + 1] = tmp;
          tmp = qty[j];
          qty[j] = qty[j + 1];
          qty[j + 1] = tmp;
          swap = true;
        }
      }
    } while(swap);
  }

  private void calculate(List<Integer> store) {
    this.initLimit();
    boolean start = true;
    boolean chaloo = true;
    int best = 0;
    boolean sum = false;
    comb = new int[total];

    while(true) {
      while(start) {
        combinations();
        int var7 = 0;

        int i;
        for(i = 0; i < total; ++i) {
          var7 += block[i] * comb[i];
          if(var7 > max) {
            var7 = 0;
            break;
          }
        }

        if(var7 > 0) {
          if(var7 == max) {
            showComb(0, store);
            resetComb();
            updateLimit();
            best = 0;
            sum = false;
          } else if(var7 > best) {
            best = var7;
            tempCombination = new int[total];

            for(i = 0; i < total; ++i) {
              tempCombination[i] = comb[i];
            }

            sum = false;
          }
        }

        for(i = 0; i < total; ++i) {
          if(comb[i] != limit[i]) {
            chaloo = true;
            break;
          }

          chaloo = false;
        }

        if(!chaloo) {
          showComb(best, store);
          updateLimit();
          resetComb();
          best = 0;
        }

        for(i = 0; i < total; ++i) {
          if(qty[i] != 0 || i == total - 1) {
            if(i == total - 1 && qty[i] == 0) {
              start = false;
            }
            break;
          }
        }
      }

      return;
    }
  }

  private void showComb(int a, List<Integer> store) {
    ++counter;
    boolean flag = false;
    Map<Integer,Integer> tempMap;
    int i;
    if(a == 0) {
      tempMap = new HashMap<>();

      for(i = 0; i < total; ++i) {
        if(comb[i] != 0) {
          tempMap.put(new Integer(block[i]), new Integer(comb[i]));
          qty[i] -= comb[i];
          if(qty[i] - comb[i] < 0) {
            flag = true;
          }
        }
      }

      if(flag) {
        mapList.add(tempMap);
        return;
      }

      showComb(0, store);
    } else {
      tempMap = new HashMap<Integer,Integer>();

      for(i = 0; i < total; ++i) {
        if(tempCombination[i] != 0) {
          tempMap.put(new Integer(this.block[i]), new Integer(tempCombination[i]));
        }
      }

      mapList.add(tempMap);
      waste += this.max - a;
      store.add(Integer.valueOf(this.max - a));

      for(i = 0; i < this.total; ++i) {
        qty[i] -= tempCombination[i];
      }

      for(i = 0; i < total; ++i) {
        if(qty[i] - comb[i] < 0) {
          return;
        }
      }

      showComb(a, store);
    }
  }

  private void combinations() {
    int i = this.total - 1;

    while(true) {
      while(comb[i] == limit[i]) {
        if(i == 0 && comb[0] != limit[0]) {
          i = total - 1;
        } else {
          comb[i] = 0;
          --i;
        }
      }

      ++comb[i];
      return;
    }
  }

  private void initLimit() {
    limit = new int[this.total];

    for(int i = 0; i < total; ++i) {
      int div = max / this.block[i];
      if(qty[i] > div) {
        limit[i] = div;
      } else {
        limit[i] = qty[i];
      }
    }
  }

  private void updateLimit() {
    for(int i = 0; i < total; ++i) {
      if(qty[i] < limit[i]) {
        limit[i] = qty[i];
      }
    }
  }

  private void resetComb() {
    Arrays.fill(comb, 0);
  }
}