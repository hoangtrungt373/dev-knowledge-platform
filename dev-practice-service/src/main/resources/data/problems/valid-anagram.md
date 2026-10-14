---
# Seeded by service.seed.ProblemSeeder — see evaluate-reverse-polish-notation.md for the file format.
# Every expectedOutput is checked by ProblemSeederTest, which runs every implementation below against
# each test case (Java in-process, Python/JavaScript as the real judge program under a local
# interpreter); PUBLISHED is then reached through the judge (publish-on-accept). Java is written for
# Judge0's OpenJDK 13 using only java.util (the harness prelude imports it); Python for 3.8; JavaScript
# for Node.js 12. The first implementation listed (Sorting's JAVA) publishes the problem.
title: Valid Anagram
difficulty: EASY
status: PUBLISHED
tags: [Hash Table, String, Sorting]
template:
  language: JAVA
  code: |
    class Solution {
        public boolean isAnagram(String s, String t) {

        }
    }
solutions:
  - title: Sorting
    explanation: |
      Two strings are anagrams exactly when they hold the same letters the same number of times — and
      sorting puts any arrangement of those letters into one canonical order. So sort both strings'
      characters and compare the results.

      Strings of different lengths can never be anagrams, so check that first: it's free, and it skips
      both sorts.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n log n)` — the two sorts dominate.
      - Space: `O(n)` for the sorted copies (strings are immutable in all three languages).
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public boolean isAnagram(String s, String t) {
                  if (s.length() != t.length()) {
                      return false;
                  }
                  char[] a = s.toCharArray();
                  char[] b = t.toCharArray();
                  Arrays.sort(a);
                  Arrays.sort(b);
                  return Arrays.equals(a, b);
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def isAnagram(self, s: str, t: str) -> bool:
                  if len(s) != len(t):
                      return False
                  return sorted(s) == sorted(t)
      - language: JAVASCRIPT
        code: |
          /**
           * @param {string} s
           * @param {string} t
           * @return {boolean}
           */
          var isAnagram = function(s, t) {
              if (s.length !== t.length) {
                  return false;
              }
              return s.split("").sort().join("") === t.split("").sort().join("");
          };
  - title: Character Count
    explanation: |
      Instead of putting the letters in order, count them. The input is only lowercase English
      letters, so the "hash table" can be a plain array of 26 counters, indexed by `letter - 'a'`.

      Walk both strings together: each letter of `s` adds one to its counter and each letter of `t`
      takes one away. They're anagrams exactly when every counter ends at zero — any non-zero counter
      is a letter one string has more of.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n)` — one pass, plus a constant 26-slot check.
      - Space: `O(1)` — the array has 26 slots whatever the input size.

      For arbitrary Unicode input, swap the array for a hash map from character to count — same
      algorithm, but `O(k)` space for `k` distinct characters.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public boolean isAnagram(String s, String t) {
                  if (s.length() != t.length()) {
                      return false;
                  }
                  int[] counts = new int[26];
                  for (int i = 0; i < s.length(); i++) {
                      counts[s.charAt(i) - 'a']++;
                      counts[t.charAt(i) - 'a']--;
                  }
                  for (int count : counts) {
                      if (count != 0) {
                          return false;
                      }
                  }
                  return true;
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def isAnagram(self, s: str, t: str) -> bool:
                  if len(s) != len(t):
                      return False
                  counts = [0] * 26
                  for a, b in zip(s, t):
                      counts[ord(a) - ord("a")] += 1
                      counts[ord(b) - ord("a")] -= 1
                  return all(count == 0 for count in counts)
      - language: JAVASCRIPT
        code: |
          /**
           * @param {string} s
           * @param {string} t
           * @return {boolean}
           */
          var isAnagram = function(s, t) {
              if (s.length !== t.length) {
                  return false;
              }
              const counts = new Array(26).fill(0);
              for (let i = 0; i < s.length; i++) {
                  counts[s.charCodeAt(i) - 97]++;
                  counts[t.charCodeAt(i) - 97]--;
              }
              return counts.every(count => count === 0);
          };
testCases:
  # Examples 1–3 from the description — shown to users.
  - input: '["racecar","carrace"]'
    expectedOutput: 'true'
    sample: true
  - input: '["jar","jam"]'
    expectedOutput: 'false'
    sample: true
  - input: '["x","x"]'
    expectedOutput: 'true'
    sample: true
  - input: '["anagram","nagaram"]'
    expectedOutput: 'true'
    sample: false
  - input: '["listen","silent"]'
    expectedOutput: 'true'
    sample: false
  - input: '["rat","car"]'
    expectedOutput: 'false'
    sample: false
  - input: '["a","b"]'
    expectedOutput: 'false'
    sample: false
  # Different lengths, one a prefix of the other (catches counting only over s, or only up to the shorter length).
  - input: '["a","ab"]'
    expectedOutput: 'false'
    sample: false
  - input: '["ab","a"]'
    expectedOutput: 'false'
    sample: false
  # Same set of letters, different counts (catches comparing letter sets instead of counts).
  - input: '["aab","abb"]'
    expectedOutput: 'false'
    sample: false
  - input: '["abcc","abbc"]'
    expectedOutput: 'false'
    sample: false
  # Equal sums of character codes, 'a'+'d' == 'b'+'c' (catches a sum-of-codes shortcut).
  - input: '["ad","bc"]'
    expectedOutput: 'false'
    sample: false
  # XOR of each string is 0 (catches an XOR shortcut).
  - input: '["aa","bb"]'
    expectedOutput: 'false'
    sample: false
  - input: '["aaaa","aaaa"]'
    expectedOutput: 'true'
    sample: false
  - input: '["aaaa","aaab"]'
    expectedOutput: 'false'
    sample: false
  # Every letter once, in opposite orders — exercises all 26 counters.
  - input: '["abcdefghijklmnopqrstuvwxyz","zyxwvutsrqponmlkjihgfedcba"]'
    expectedOutput: 'true'
    sample: false
  - input: '["thequickbrownfoxjumpsoverthelazydog","godyzalehtrevospmujxofnworbkciuqeht"]'
    expectedOutput: 'true'
    sample: false
---
Given two strings `s` and `t`, return `true` if the two strings are **anagrams** of each other,
otherwise return `false`.

Two strings are anagrams if they contain the same characters, with each character appearing the same
number of times, regardless of order.

**Example 1:**

```
Input: s = "racecar", t = "carrace"
Output: true
```

**Example 2:**

```
Input: s = "jar", t = "jam"
Output: false
```

**Example 3:**

```
Input: s = "x", t = "x"
Output: true
```

**Constraints:**

- `1 <= s.length, t.length <= 5 * 10^4`
- `s` and `t` consist of lowercase English letters.
