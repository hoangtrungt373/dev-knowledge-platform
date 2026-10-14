---
# Seeded by service.seed.ProblemSeeder. The signature comes from `template` (parsed exactly like the
# admin form's "Parse template" button); the Markdown body below the closing --- is the description.
# Every expectedOutput was computed with a reference implementation, not by hand.
title: Evaluate Reverse Polish Notation
difficulty: MEDIUM
# PUBLISHED is reached through verification, not set directly: the seeder creates the problem as a
# DRAFT, submits the first implementation in `solutions` as a publish-on-accept reference run, and the
# judge publishes it once every test case passes (DKP-0056/0057). If the judge is unreachable it stays
# a DRAFT.
status: PUBLISHED
tags: [Array, Math, Stack]
template:
  language: JAVA
  code: |
    class Solution {
        public int evalRPN(String[] tokens) {

        }
    }
# The only place code lives. Each approach (in display order) has a title, a Markdown explanation and
# implementations ({language, code}); every implementation is submitted as its own REFERENCE run, judged
# like any submission, and linked to its approach. ProblemSeederTest compiles and runs every one against
# every test case below, so a wrong expectedOutput (or a wrong implementation) fails the build. Java is
# written for Judge0's OpenJDK 13 (no arrow-switch), using only java.util — the harness prelude imports
# it; Python for Judge0's Python 3.8 and the test's local interpreter (no 3.8-only syntax), with
# typing.List imported by its prelude; JavaScript for Judge0's Node.js 12 (no ?? or ?.), in LeetCode's
# `var name = function(...)` style. The first implementation listed (Java) publishes the problem.
# Explanations contain no hand-written code: {{code:JAVA}} on a line of its own is replaced, when served,
# by this approach's verified JAVA run (CodePlaceholders).
solutions:
  - title: Stack
    explanation: |
      In Reverse Polish Notation every operator comes **after** its two operands, so the expression can
      be evaluated left to right with a stack:

      1. A number: push it.
      2. An operator: pop the right operand, then the left one, apply the operator, push the result.

      The single value left on the stack at the end is the answer.

      {{code:JAVA}}

      Order matters for `-` and `/`: the first value popped is the **right** operand. Division must
      truncate toward zero: Java's integer `/` already does; Python's `//` floors (`-7 // 2` is `-4`),
      so the Python version divides the magnitudes and then restores the sign; JavaScript's `/` gives a
      fraction, so it wraps the result in `Math.trunc`.

      **Complexity**

      - Time: `O(n)` — one pass with `O(1)` work per token.
      - Space: `O(n)` for the stack in the worst case (all numbers first).
    implementations:
      - language: JAVA
        code: |
          class Solution {
              public int evalRPN(String[] tokens) {
                  Deque<Integer> stack = new ArrayDeque<>();
                  for (String token : tokens) {
                      switch (token) {
                          case "+": stack.push(stack.pop() + stack.pop()); break;
                          case "*": stack.push(stack.pop() * stack.pop()); break;
                          case "-": { int b = stack.pop(); stack.push(stack.pop() - b); break; }
                          case "/": { int b = stack.pop(); stack.push(stack.pop() / b); break; }
                          default: stack.push(Integer.parseInt(token));
                      }
                  }
                  return stack.pop();
              }
          }
      - language: PYTHON
        code: |
          class Solution:
              def evalRPN(self, tokens: List[str]) -> int:
                  stack = []
                  for token in tokens:
                      if token in ("+", "-", "*", "/"):
                          b = stack.pop()
                          a = stack.pop()
                          if token == "+":
                              stack.append(a + b)
                          elif token == "-":
                              stack.append(a - b)
                          elif token == "*":
                              stack.append(a * b)
                          else:
                              # Truncate toward zero like the problem asks; Python's // floors instead
                              # (-7 // 2 == -4), so divide the magnitudes and restore the sign.
                              quotient = abs(a) // abs(b)
                              stack.append(quotient if (a < 0) == (b < 0) else -quotient)
                      else:
                          stack.append(int(token))
                  return stack.pop()
      - language: JAVASCRIPT
        code: |
          /**
           * @param {string[]} tokens
           * @return {number}
           */
          var evalRPN = function(tokens) {
              const stack = [];
              for (const token of tokens) {
                  if (token === "+" || token === "-" || token === "*" || token === "/") {
                      const b = stack.pop();
                      const a = stack.pop();
                      if (token === "+") {
                          stack.push(a + b);
                      } else if (token === "-") {
                          stack.push(a - b);
                      } else if (token === "*") {
                          stack.push(a * b);
                      } else {
                          // Plain / gives a fraction; Math.trunc drops it toward zero, as the problem asks.
                          stack.push(Math.trunc(a / b));
                      }
                  } else {
                      stack.push(Number(token));
                  }
              }
              return stack.pop();
          };
testCases:
  # Example 1 from the description — shown to users.
  - input: '[["1","2","+","3","*","4","-"]]'
    expectedOutput: '5'
    sample: true
  - input: '[["2","1","+","3","*"]]'
    expectedOutput: '9'
    sample: true
  # Division truncates: 13 / 5 = 2.
  - input: '[["4","13","5","/","+"]]'
    expectedOutput: '6'
    sample: false
  # 6 / -132 truncates to 0, which zeroes the left side before + 17 + 5.
  - input: '[["10","6","9","3","+","-11","*","/","*","17","+","5","+"]]'
    expectedOutput: '22'
    sample: false
  # A single operand, no operator at all.
  - input: '[["42"]]'
    expectedOutput: '42'
    sample: false
  - input: '[["-7"]]'
    expectedOutput: '-7'
    sample: false
  # Operand order: 3 - 5, not 5 - 3.
  - input: '[["3","5","-"]]'
    expectedOutput: '-2'
    sample: false
  # Truncation toward zero for negative results: -3, not -4 (floor division would give -4).
  - input: '[["-7","2","/"]]'
    expectedOutput: '-3'
    sample: false
  - input: '[["7","-2","/"]]'
    expectedOutput: '-3'
    sample: false
  - input: '[["-6","3","/"]]'
    expectedOutput: '-2'
    sample: false
  - input: '[["0","3","/"]]'
    expectedOutput: '0'
    sample: false
  # The constraint's extremes multiplied together.
  - input: '[["-200","-200","*"]]'
    expectedOutput: '40000'
    sample: false
  - input: '[["5","1","2","+","4","*","+","3","-"]]'
    expectedOutput: '14'
    sample: false
  - input: '[["100","200","+","2","/","5","*","7","+"]]'
    expectedOutput: '757'
    sample: false
  - input: '[["1","1","+","1","+","1","+","1","+"]]'
    expectedOutput: '5'
    sample: false
  # Deep stack: 20 operands pushed before the first operator (catches a fixed-size stack).
  - input: '[["1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","+","+","+","+","+","+","+","+","+","+","+","+","+","+","+","+","+","+","+"]]'
    expectedOutput: '210'
    sample: false
  # Right-nested subtraction: 1 - (2 - (3 - (4 - 5))).
  - input: '[["1","2","3","4","5","-","-","-","-"]]'
    expectedOutput: '3'
    sample: false
  # Chained truncation: -200 / 3 = -66, * 7 = -462, / -4 = 115 (115.5 truncated).
  - input: '[["-200","3","/","7","*","-4","/"]]'
    expectedOutput: '115'
    sample: false
---
You are given an array of strings `tokens` that represents a valid arithmetic expression in
[Reverse Polish Notation](https://en.wikipedia.org/wiki/Reverse_Polish_notation).

Return the integer that represents the evaluation of the expression.

- The operands may be integers or the results of other operations.
- The operators include `'+'`, `'-'`, `'*'`, and `'/'`.
- Assume that division between integers always truncates toward zero.

**Example 1:**

```
Input: tokens = ["1","2","+","3","*","4","-"]
Output: 5
Explanation: ((1 + 2) * 3) - 4 = 5
```

**Example 2:**

```
Input: tokens = ["2","1","+","3","*"]
Output: 9
Explanation: (2 + 1) * 3 = 9
```

**Constraints:**

- `1 <= tokens.length <= 10000`
- `tokens[i]` is `"+"`, `"-"`, `"*"`, or `"/"`, or a string representing an integer in the range `[-200, 200]`.
