---
# Seeded by service.seed.ProblemSeeder — see evaluate-reverse-polish-notation.md for the file format.
# Every expectedOutput is checked by ProblemSeederTest, which compiles referenceSolution below and
# runs it against each test case; PUBLISHED is then reached through the judge (publish-on-accept).
title: Contains Duplicate
difficulty: EASY
status: PUBLISHED
tags: [Array, Hash Table, Sorting]
template:
  language: JAVA
  code: |
    class Solution {
        public boolean containsDuplicate(int[] nums) {

        }
    }
# One pass with a HashSet: O(n) time, O(n) space. Set#add returns false when the value is already
# there, which is exactly "seen before". Written for Judge0's OpenJDK 13, java.util only.
referenceSolution:
  language: JAVA
  code: |
    class Solution {
        public boolean containsDuplicate(int[] nums) {
            Set<Integer> seen = new HashSet<>();
            for (int num : nums) {
                if (!seen.add(num)) {
                    return true;
                }
            }
            return false;
        }
    }
testCases:
  # Examples 1 and 2 from the description — shown to users.
  - input: '[[1,2,3,3]]'
    expectedOutput: 'true'
    sample: true
  - input: '[[1,2,3,4]]'
    expectedOutput: 'false'
    sample: true
  # The constraints allow an empty array: nothing can repeat.
  - input: '[[]]'
    expectedOutput: 'false'
    sample: false
  - input: '[[7]]'
    expectedOutput: 'false'
    sample: false
  - input: '[[5,5]]'
    expectedOutput: 'true'
    sample: false
  - input: '[[1,1,1,1]]'
    expectedOutput: 'true'
    sample: false
  - input: '[[0,0]]'
    expectedOutput: 'true'
    sample: false
  # Negatives, and a duplicate that isn't adjacent (catches "compare neighbours" without sorting).
  - input: '[[-1,0,1,-1]]'
    expectedOutput: 'true'
    sample: false
  - input: '[[3,1,4,1,5,9,2,6]]'
    expectedOutput: 'true'
    sample: false
  # Same magnitude, opposite signs — not duplicates (catches comparing absolute values).
  - input: '[[-5,5,-6,6]]'
    expectedOutput: 'false'
    sample: false
  # The constraint's extremes.
  - input: '[[-1000000000,1000000000]]'
    expectedOutput: 'false'
    sample: false
  - input: '[[1000000000,-1000000000,1000000000]]'
    expectedOutput: 'true'
    sample: false
  # Duplicate as far apart as possible: first and last element.
  - input: '[[1,2,3,4,5,6,7,8,9,1]]'
    expectedOutput: 'true'
    sample: false
  # Duplicate in the last two positions.
  - input: '[[2,14,18,22,22]]'
    expectedOutput: 'true'
    sample: false
  - input: '[[9,8,7,6,5,4,3,2,1,0]]'
    expectedOutput: 'false'
    sample: false
  # Twenty distinct values.
  - input: '[[20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1]]'
    expectedOutput: 'false'
    sample: false
---
Given an integer array `nums`, return `true` if any value appears **more than once** in the array,
otherwise return `false`.

**Example 1:**

```
Input: nums = [1, 2, 3, 3]
Output: true
```

**Example 2:**

```
Input: nums = [1, 2, 3, 4]
Output: false
```

**Constraints:**

- `0 <= nums.length <= 10^5`
- `-10^9 <= nums[i] <= 10^9`
