---
# Seeded by service.seed.ProblemSeeder — see evaluate-reverse-polish-notation.md for the file format.
# Every expectedOutput is checked by ProblemSeederTest, which runs every implementation below against
# each test case (Java in-process, Python/JavaScript as the real judge program under a local
# interpreter); PUBLISHED is then reached through the judge (publish-on-accept). Java is written for
# Judge0's OpenJDK 13 using only java.util (the harness prelude imports it); Python for 3.8; JavaScript
# for Node.js 12. The first implementation listed (Brute Force's JAVA) publishes the problem.
#
# The output order is fixed ("smaller index first"), so the judge's ordered comparison is correct here.
# Every test case has exactly one valid pair — the two approaches find a pair in different orders
# (brute force: smallest first index; hash map: smallest second index), so they only agree when it's
# unique, and the test below fails if a case ever has two.
title: Two Sum
difficulty: EASY
status: PUBLISHED
tags: [Array, Hash Table]
template:
  language: JAVA
  code: |
    class Solution {
        public int[] twoSum(int[] nums, int target) {

        }
    }
solutions:
  - title: Brute Force
    explanation: |
      Try every pair. For each index `i`, check every later index `j`; the first pair whose values add
      up to `target` is the answer. Starting `j` at `i + 1` both skips using one element twice and
      returns the smaller index first.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n²)` — about `n²/2` pairs.
      - Space: `O(1)`.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public int[] twoSum(int[] nums, int target) {
                  for (int i = 0; i < nums.length; i++) {
                      for (int j = i + 1; j < nums.length; j++) {
                          if (nums[i] + nums[j] == target) {
                              return new int[] {i, j};
                          }
                      }
                  }
                  return new int[0];
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def twoSum(self, nums: List[int], target: int) -> List[int]:
                  for i in range(len(nums)):
                      for j in range(i + 1, len(nums)):
                          if nums[i] + nums[j] == target:
                              return [i, j]
                  return []
      - language: JAVASCRIPT
        code: |
          /**
           * @param {number[]} nums
           * @param {number} target
           * @return {number[]}
           */
          var twoSum = function(nums, target) {
              for (let i = 0; i < nums.length; i++) {
                  for (let j = i + 1; j < nums.length; j++) {
                      if (nums[i] + nums[j] === target) {
                          return [i, j];
                      }
                  }
              }
              return [];
          };
  - title: Hash Map (One Pass)
    explanation: |
      For each number, the only partner that works is `target - nums[i]` — its *complement*. Instead of
      searching for it, remember every number seen so far in a hash map from value to index; then
      "have I seen the complement?" is one `O(1)` lookup.

      Look the complement up **before** storing the current number. That way a number never pairs
      with itself (`[3, 2, 4]` with target `6` must not return `3 + 3`), while two equal numbers at
      different indices still pair (`[5, 5]` with target `10`). The index found in the map is always
      the earlier one, so the smaller index comes first without sorting.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n)` — one pass, each lookup and insert `O(1)` on average.
      - Space: `O(n)` for the map — the classic trade of memory for speed over the brute force.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public int[] twoSum(int[] nums, int target) {
                  Map<Integer, Integer> indexByValue = new HashMap<>();
                  for (int i = 0; i < nums.length; i++) {
                      Integer partner = indexByValue.get(target - nums[i]);
                      if (partner != null) {
                          return new int[] {partner, i};
                      }
                      indexByValue.put(nums[i], i);
                  }
                  return new int[0];
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def twoSum(self, nums: List[int], target: int) -> List[int]:
                  index_by_value = {}
                  for i, num in enumerate(nums):
                      partner = index_by_value.get(target - num)
                      if partner is not None:
                          return [partner, i]
                      index_by_value[num] = i
                  return []
      - language: JAVASCRIPT
        code: |
          /**
           * @param {number[]} nums
           * @param {number} target
           * @return {number[]}
           */
          var twoSum = function(nums, target) {
              const indexByValue = new Map();
              for (let i = 0; i < nums.length; i++) {
                  const partner = indexByValue.get(target - nums[i]);
                  if (partner !== undefined) {
                      return [partner, i];
                  }
                  indexByValue.set(nums[i], i);
              }
              return [];
          };
testCases:
  # Examples 1–3 from the description — shown to users.
  - input: '[[3,4,5,6],7]'
    expectedOutput: '[0,1]'
    sample: true
  - input: '[[4,5,6],10]'
    expectedOutput: '[0,2]'
    sample: true
  - input: '[[5,5],10]'
    expectedOutput: '[0,1]'
    sample: true
  - input: '[[2,7,11,15],9]'
    expectedOutput: '[0,1]'
    sample: false
  # 3 + 3 == 6, but it's one element used twice (catches pairing an index with itself).
  - input: '[[3,2,4],6]'
    expectedOutput: '[1,2]'
    sample: false
  - input: '[[3,3],6]'
    expectedOutput: '[0,1]'
    sample: false
  # Equal values next to each other, mid-array.
  - input: '[[2,5,5,11],10]'
    expectedOutput: '[1,2]'
    sample: false
  # Negatives; -4 + -4 == -8 would be one element used twice.
  - input: '[[-1,-2,-3,-4,-5],-8]'
    expectedOutput: '[2,4]'
    sample: false
  # Zeros: the pair is the first and last element, and target 0.
  - input: '[[0,4,3,0],0]'
    expectedOutput: '[0,3]'
    sample: false
  - input: '[[1,9,2,8,3,6],7]'
    expectedOutput: '[0,5]'
    sample: false
  # The pair is the last two elements.
  - input: '[[1,2,3,4,5,6,7,8,9,10],19]'
    expectedOutput: '[8,9]'
    sample: false
  # The constraint's extremes.
  - input: '[[-10000000,10000000],0]'
    expectedOutput: '[0,1]'
    sample: false
  - input: '[[1,5,10000000,-3],9999997]'
    expectedOutput: '[2,3]'
    sample: false
  # The pair spans the whole array (catches returning the larger index first).
  - input: '[[7,1,2,8],15]'
    expectedOutput: '[0,3]'
    sample: false
---
Given an array of integers `nums` and an integer `target`, return the indices `i` and `j` such that
`nums[i] + nums[j] == target` and `i != j`.

You may assume that every input has **exactly one** pair of indices `i` and `j` that satisfy the
condition.

Return the answer with the **smaller index first**.

**Example 1:**

```
Input: nums = [3,4,5,6], target = 7
Output: [0,1]
Explanation: nums[0] + nums[1] == 7, so we return [0, 1].
```

**Example 2:**

```
Input: nums = [4,5,6], target = 10
Output: [0,2]
```

**Example 3:**

```
Input: nums = [5,5], target = 10
Output: [0,1]
```

**Constraints:**

- `2 <= nums.length <= 1000`
- `-10,000,000 <= nums[i] <= 10,000,000`
- `-10,000,000 <= target <= 10,000,000`
- Only one valid answer exists.
