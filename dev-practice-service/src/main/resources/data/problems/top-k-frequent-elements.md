---
# Seeded by service.seed.ProblemSeeder — see evaluate-reverse-polish-notation.md for the file format.
# Every expectedOutput is checked by ProblemSeederTest, which runs every implementation below against
# each test case (Java in-process, Python/JavaScript as the real judge program under a local
# interpreter), compared the way the judge compares: here ANY_ORDER. Java is written for Judge0's
# OpenJDK 13 using only java.util (the harness prelude imports it); Python for 3.8 (heapq imported by
# the solution itself — user code sits at module level); JavaScript for Node.js 12, which has no
# priority queue, so its heap approach carries a small binary heap. The first implementation listed
# (Sorting's JAVA) publishes the problem.
#
# Every case has a unique answer (no tie for the k-th place), as the problem promises — with a tie,
# different correct implementations could return different elements and this test couldn't tell.
title: Top K Frequent Elements
difficulty: MEDIUM
status: PUBLISHED
# "You may return the output in any order" (DKP-0062).
outputOrder: ANY_ORDER
tags: [Array, Hash Table, Divide and Conquer, Sorting, Heap, Bucket Sort, Counting, Quickselect]
template:
  language: JAVA
  code: |
    class Solution {
        public int[] topKFrequent(int[] nums, int k) {

        }
    }
solutions:
  - title: Sorting
    explanation: |
      Count how often each value appears with a hash map, then sort the distinct values by their count,
      most frequent first, and take the first `k`.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n log n)` — counting is `O(n)`; sorting up to `n` distinct values dominates.
      - Space: `O(n)` for the counts.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public int[] topKFrequent(int[] nums, int k) {
                  Map<Integer, Integer> counts = new HashMap<>();
                  for (int num : nums) {
                      counts.merge(num, 1, Integer::sum);
                  }
                  List<Integer> values = new ArrayList<>(counts.keySet());
                  values.sort((a, b) -> counts.get(b) - counts.get(a));
                  int[] result = new int[k];
                  for (int i = 0; i < k; i++) {
                      result[i] = values.get(i);
                  }
                  return result;
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def topKFrequent(self, nums: List[int], k: int) -> List[int]:
                  counts = {}
                  for num in nums:
                      counts[num] = counts.get(num, 0) + 1
                  values = sorted(counts, key=lambda v: counts[v], reverse=True)
                  return values[:k]
      - language: JAVASCRIPT
        code: |
          /**
           * @param {number[]} nums
           * @param {number} k
           * @return {number[]}
           */
          var topKFrequent = function(nums, k) {
              const counts = new Map();
              for (const num of nums) {
                  counts.set(num, (counts.get(num) || 0) + 1);
              }
              const values = Array.from(counts.keys());
              values.sort((a, b) => counts.get(b) - counts.get(a));
              return values.slice(0, k);
          };
  - title: Min-Heap of Size k
    explanation: |
      Only the `k` most frequent values matter, so there's no need to sort all of them. Keep a
      **min-heap** of at most `k` values, ordered by count: push each distinct value, and whenever the
      heap grows past `k`, pop its smallest — the least frequent value seen so far can't be in the
      answer. What's left at the end is exactly the top `k`.

      A *min*-heap, not a max-heap, is the point: the value to throw away is always the least frequent
      one kept, and that's the one a min-heap has on top.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n log k)` — each push/pop costs `log k`, since the heap never holds more than `k + 1`.
      - Space: `O(n)` for the counts, `O(k)` for the heap.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public int[] topKFrequent(int[] nums, int k) {
                  Map<Integer, Integer> counts = new HashMap<>();
                  for (int num : nums) {
                      counts.merge(num, 1, Integer::sum);
                  }
                  PriorityQueue<Integer> heap = new PriorityQueue<>((a, b) -> counts.get(a) - counts.get(b));
                  for (int value : counts.keySet()) {
                      heap.add(value);
                      if (heap.size() > k) {
                          heap.poll();
                      }
                  }
                  int[] result = new int[k];
                  for (int i = 0; i < k; i++) {
                      result[i] = heap.poll();
                  }
                  return result;
              }
          }
      - language: PYTHON
        code: |
          import heapq


          class Solution:
              def topKFrequent(self, nums: List[int], k: int) -> List[int]:
                  counts = {}
                  for num in nums:
                      counts[num] = counts.get(num, 0) + 1
                  heap = []
                  for value, count in counts.items():
                      heapq.heappush(heap, (count, value))
                      if len(heap) > k:
                          heapq.heappop(heap)
                  return [value for count, value in heap]
      - language: JAVASCRIPT
        code: |
          /**
           * @param {number[]} nums
           * @param {number} k
           * @return {number[]}
           */
          var topKFrequent = function(nums, k) {
              const counts = new Map();
              for (const num of nums) {
                  counts.set(num, (counts.get(num) || 0) + 1);
              }
              // JavaScript has no built-in priority queue: a binary min-heap of [count, value] pairs.
              const heap = [];
              const swap = (i, j) => { const t = heap[i]; heap[i] = heap[j]; heap[j] = t; };
              const push = (item) => {
                  heap.push(item);
                  let i = heap.length - 1;
                  while (i > 0) {
                      const parent = (i - 1) >> 1;
                      if (heap[parent][0] <= heap[i][0]) break;
                      swap(i, parent);
                      i = parent;
                  }
              };
              const pop = () => {
                  const top = heap[0];
                  const last = heap.pop();
                  if (heap.length > 0) {
                      heap[0] = last;
                      let i = 0;
                      while (true) {
                          const left = 2 * i + 1, right = left + 1;
                          let smallest = i;
                          if (left < heap.length && heap[left][0] < heap[smallest][0]) smallest = left;
                          if (right < heap.length && heap[right][0] < heap[smallest][0]) smallest = right;
                          if (smallest === i) break;
                          swap(i, smallest);
                          i = smallest;
                      }
                  }
                  return top;
              };
              for (const [value, count] of counts) {
                  push([count, value]);
                  if (heap.length > k) {
                      pop();
                  }
              }
              return heap.map(item => item[1]);
          };
  - title: Bucket Sort
    explanation: |
      A count can never exceed `n`, the array's length — so instead of comparing counts, use them as
      **indices**. Make `n + 1` buckets, put each distinct value into the bucket for its count, then
      walk the buckets from the highest count down, collecting values until there are `k`.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n)` — counting, filling the buckets and one walk over them are each linear.
      - Space: `O(n)` for the counts and the buckets.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public int[] topKFrequent(int[] nums, int k) {
                  Map<Integer, Integer> counts = new HashMap<>();
                  for (int num : nums) {
                      counts.merge(num, 1, Integer::sum);
                  }
                  List<List<Integer>> buckets = new ArrayList<>();
                  for (int i = 0; i <= nums.length; i++) {
                      buckets.add(new ArrayList<>());
                  }
                  for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                      buckets.get(entry.getValue()).add(entry.getKey());
                  }
                  int[] result = new int[k];
                  int filled = 0;
                  for (int count = nums.length; count > 0 && filled < k; count--) {
                      for (int value : buckets.get(count)) {
                          if (filled == k) {
                              break;
                          }
                          result[filled++] = value;
                      }
                  }
                  return result;
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def topKFrequent(self, nums: List[int], k: int) -> List[int]:
                  counts = {}
                  for num in nums:
                      counts[num] = counts.get(num, 0) + 1
                  buckets = [[] for _ in range(len(nums) + 1)]
                  for value, count in counts.items():
                      buckets[count].append(value)
                  result = []
                  for count in range(len(nums), 0, -1):
                      for value in buckets[count]:
                          result.append(value)
                          if len(result) == k:
                              return result
                  return result
      - language: JAVASCRIPT
        code: |
          /**
           * @param {number[]} nums
           * @param {number} k
           * @return {number[]}
           */
          var topKFrequent = function(nums, k) {
              const counts = new Map();
              for (const num of nums) {
                  counts.set(num, (counts.get(num) || 0) + 1);
              }
              const buckets = [];
              for (let i = 0; i <= nums.length; i++) {
                  buckets.push([]);
              }
              for (const [value, count] of counts) {
                  buckets[count].push(value);
              }
              const result = [];
              for (let count = nums.length; count > 0; count--) {
                  for (const value of buckets[count]) {
                      result.push(value);
                      if (result.length === k) {
                          return result;
                      }
                  }
              }
              return result;
          };
testCases:
  # Examples 1–2 from the description — shown to users.
  - input: '[[1,2,2,3,3,3],2]'
    expectedOutput: '[2,3]'
    sample: true
  - input: '[[7,7],1]'
    expectedOutput: '[7]'
    sample: true
  - input: '[[1],1]'
    expectedOutput: '[1]'
    sample: false
  # The most frequent value first in the array (catches "first k distinct values").
  - input: '[[1,1,1,2,2,3],2]'
    expectedOutput: '[1,2]'
    sample: false
  - input: '[[4,4,4,5,5,6],1]'
    expectedOutput: '[4]'
    sample: false
  # The most frequent value last, after values with smaller counts.
  - input: '[[1,2,3,4,4],1]'
    expectedOutput: '[4]'
    sample: false
  # Negatives, and a value whose count is larger than its magnitude.
  - input: '[[-1,-1,-2,-2,-2,3],2]'
    expectedOutput: '[-2,-1]'
    sample: false
  - input: '[[3,0,1,0],1]'
    expectedOutput: '[0]'
    sample: false
  # k equal to the number of distinct values: everything comes back.
  - input: '[[5,3,5,1,3,5],3]'
    expectedOutput: '[5,3,1]'
    sample: false
  # Frequency order differs from value order (catches sorting by value instead of count).
  - input: '[[6,6,6,6,2,2,2,9,9,1],3]'
    expectedOutput: '[6,2,9]'
    sample: false
  - input: '[[2,2,3,3,3,4,4,4,4,5,5,5,5,5],2]'
    expectedOutput: '[5,4]'
    sample: false
  # The constraint's extremes.
  - input: '[[1000,-1000,1000],1]'
    expectedOutput: '[1000]'
    sample: false
  # Every value equally frequent except one: all its occurrences in the highest bucket.
  - input: '[[8,8,8,8,8,8,8,8,1,2,3,4,5,6,7],1]'
    expectedOutput: '[8]'
    sample: false
---
Given an integer array `nums` and an integer `k`, return the `k` **most frequent** elements within
the array.

The test cases are generated such that the answer is always **unique**.

You may return the output in **any order**.

**Example 1:**

```
Input: nums = [1,2,2,3,3,3], k = 2
Output: [2,3]
```

**Example 2:**

```
Input: nums = [7,7], k = 1
Output: [7]
```

**Constraints:**

- `1 <= nums.length <= 10^4`
- `-1000 <= nums[i] <= 1000`
- `1 <= k <=` number of distinct elements in `nums`
