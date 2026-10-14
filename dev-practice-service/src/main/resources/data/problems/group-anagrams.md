---
# Seeded by service.seed.ProblemSeeder — see evaluate-reverse-polish-notation.md for the file format.
# Every expectedOutput is checked by ProblemSeederTest, which runs every implementation below against
# each test case (Java in-process, Python/JavaScript as the real judge program under a local
# interpreter), compared the way the judge compares: here ANY_ORDER, so an expectedOutput may list the
# groups, and the words in each group, in any order. Java is written for Judge0's OpenJDK 13 using only
# java.util (the harness prelude imports it); Python for 3.8; JavaScript for Node.js 12. The first
# implementation listed (Sorted Key's JAVA) publishes the problem.
title: Group Anagrams
difficulty: MEDIUM
status: PUBLISHED
# "You may return the output in any order" — groups and the words inside them (DKP-0062).
outputOrder: ANY_ORDER
tags: [Array, Hash Table, String, Sorting]
template:
  language: JAVA
  code: |
    class Solution {
        public List<List<String>> groupAnagrams(String[] strs) {

        }
    }
solutions:
  - title: Sorted Key
    explanation: |
      Anagrams contain the same letters, so sorting any of them gives the same string — `"eat"`,
      `"tea"` and `"ate"` all sort to `"aet"`. That sorted string is a *key* every word in a group
      shares and no word outside it does.

      Walk the words once, and file each one under its key in a hash map from key to list of words.
      The map's values are the groups.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n · k log k)` for `n` words of length up to `k` — one sort per word.
      - Space: `O(n · k)` for the keys and the grouped words.
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public List<List<String>> groupAnagrams(String[] strs) {
                  Map<String, List<String>> groups = new HashMap<>();
                  for (String word : strs) {
                      char[] letters = word.toCharArray();
                      Arrays.sort(letters);
                      String key = new String(letters);
                      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
                  }
                  return new ArrayList<>(groups.values());
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def groupAnagrams(self, strs: List[str]) -> List[List[str]]:
                  groups = {}
                  for word in strs:
                      key = "".join(sorted(word))
                      groups.setdefault(key, []).append(word)
                  return list(groups.values())
      - language: JAVASCRIPT
        code: |
          /**
           * @param {string[]} strs
           * @return {string[][]}
           */
          var groupAnagrams = function(strs) {
              const groups = new Map();
              for (const word of strs) {
                  const key = word.split("").sort().join("");
                  if (!groups.has(key)) {
                      groups.set(key, []);
                  }
                  groups.get(key).push(word);
              }
              return Array.from(groups.values());
          };
  - title: Character Count Key
    explanation: |
      Sorting each word costs `O(k log k)`, but the words only contain the 26 lowercase letters — so
      count them instead, in `O(k)`. Two words are anagrams exactly when their 26 counts match, so the
      counts themselves can be the key.

      Turn the counts into a key **with a separator between them**. Without one, different counts can
      run together into the same string: `b×1, d×10` and `b×10, c×1` both concatenate to `0101…`.

      {{code:JAVA}}

      **Complexity**

      - Time: `O(n · k)` — one pass over every letter, plus a constant 26-slot key per word.
      - Space: `O(n · k)` for the grouped words (each key is a fixed 26 counts).
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public List<List<String>> groupAnagrams(String[] strs) {
                  Map<String, List<String>> groups = new HashMap<>();
                  for (String word : strs) {
                      int[] counts = new int[26];
                      for (int i = 0; i < word.length(); i++) {
                          counts[word.charAt(i) - 'a']++;
                      }
                      // Arrays.toString separates the counts: "[1, 0, ...]".
                      String key = Arrays.toString(counts);
                      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
                  }
                  return new ArrayList<>(groups.values());
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def groupAnagrams(self, strs: List[str]) -> List[List[str]]:
                  groups = {}
                  for word in strs:
                      counts = [0] * 26
                      for letter in word:
                          counts[ord(letter) - ord("a")] += 1
                      # A tuple is hashable and keeps the counts apart — no separator needed.
                      groups.setdefault(tuple(counts), []).append(word)
                  return list(groups.values())
      - language: JAVASCRIPT
        code: |
          /**
           * @param {string[]} strs
           * @return {string[][]}
           */
          var groupAnagrams = function(strs) {
              const groups = new Map();
              for (const word of strs) {
                  const counts = new Array(26).fill(0);
                  for (let i = 0; i < word.length; i++) {
                      counts[word.charCodeAt(i) - 97]++;
                  }
                  const key = counts.join("#");
                  if (!groups.has(key)) {
                      groups.set(key, []);
                  }
                  groups.get(key).push(word);
              }
              return Array.from(groups.values());
          };
testCases:
  # Examples 1–3 from the description — shown to users.
  - input: '[["act","pots","tops","cat","stop","hat"]]'
    expectedOutput: '[["hat"],["act","cat"],["stop","pots","tops"]]'
    sample: true
  - input: '[["x"]]'
    expectedOutput: '[["x"]]'
    sample: true
  - input: '[[""]]'
    expectedOutput: '[[""]]'
    sample: true
  - input: '[["eat","tea","tan","ate","nat","bat"]]'
    expectedOutput: '[["bat"],["nat","tan"],["ate","eat","tea"]]'
    sample: false
  # Repeated words stay in the group as many times as they appear.
  - input: '[["",""]]'
    expectedOutput: '[["",""]]'
    sample: false
  - input: '[["a","b","a"]]'
    expectedOutput: '[["a","a"],["b"]]'
    sample: false
  # Same letters, different lengths — not anagrams.
  - input: '[["ab","ba","abc"]]'
    expectedOutput: '[["ab","ba"],["abc"]]'
    sample: false
  # Same set of letters, different counts (catches a key built from the set of letters).
  - input: '[["aab","abb"]]'
    expectedOutput: '[["aab"],["abb"]]'
    sample: false
  # Equal sums of character codes, 'a'+'d' == 'b'+'c' (catches a sum-of-codes key).
  - input: '[["ad","bc"]]'
    expectedOutput: '[["ad"],["bc"]]'
    sample: false
  # b×1,d×10 and b×10,c×1: count keys joined without a separator would both read "0101…".
  - input: '[["bdddddddddd","bbbbbbbbbbc"]]'
    expectedOutput: '[["bdddddddddd"],["bbbbbbbbbbc"]]'
    sample: false
  - input: '[["abc","def","ghi"]]'
    expectedOutput: '[["abc"],["def"],["ghi"]]'
    sample: false
  - input: '[["listen","silent","enlist","google","gooegl"]]'
    expectedOutput: '[["listen","silent","enlist"],["google","gooegl"]]'
    sample: false
  - input: '[["evil","vile","live","veil","vill"]]'
    expectedOutput: '[["evil","vile","live","veil"],["vill"]]'
    sample: false
---
Given an array of strings `strs`, group all **anagrams** together into sublists. You may return the
output in **any order**.

An anagram is a string that contains the exact same characters as another string, but the order of
the characters can be different.

**Example 1:**

```
Input: strs = ["act","pots","tops","cat","stop","hat"]
Output: [["hat"],["act", "cat"],["stop", "pots", "tops"]]
```

**Example 2:**

```
Input: strs = ["x"]
Output: [["x"]]
```

**Example 3:**

```
Input: strs = [""]
Output: [[""]]
```

**Constraints:**

- `1 <= strs.length <= 10000`
- `0 <= strs[i].length <= 100`
- `strs[i]` is made up of lowercase English letters.
