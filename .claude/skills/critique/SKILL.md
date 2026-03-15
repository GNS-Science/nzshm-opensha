---
name: critique
description: Critically evaluate Claude's most recent output (summary, plan, design, code) for completeness, correctness, and unintended consequences
user_invocable: true
---

# /critique — Self-Critique

You must critically evaluate whatever you most recently presented to the user with the aim to improve it. Identify the type of output and apply the relevant critique framework below. Be honest and specific — vague praise is useless.

## Identify the output type

Look at your most recent substantive output and classify it as one of: **summary of goals**, **plan/design**, **code/implementation**, **trade-off decision**, or **analysis/explanation**. Then apply the matching framework.

## Summary of Goals

- Does this capture everything the user actually asked for?
- Are success criteria explicit and measurable?
- Is error handling / failure modes covered?
- Are there unstated assumptions?
- What's missing? (e.g., for a backup system — did we consider restore? For an API — did we consider auth, rate limiting, versioning?)
- Are there unintended consequences of the stated goals?

## Plan or Design

- Does this plan actually meet the user's stated goals?
- Are there simpler alternatives that weren't considered?
- Is testing sufficient? Are edge cases covered?
- Does this implementation have unintended consequences (performance, breaking changes, security)?
- Are dependencies and ordering correct?
- What could go wrong during execution?
- Is anything over-engineered or under-engineered?

## Code or Implementation

- Does this code do what was asked?
- Are there bugs, edge cases, or error conditions not handled?
- Does it follow the project's conventions (see CLAUDE.md)?
- Are there performance concerns?
- Is it testable? Were tests included?
- Could this break existing functionality?
- Is there unnecessary complexity?

## Trade-off Decision or Recommendation

- Were all reasonable options identified, or were some overlooked?
- Were the options evaluated fairly, or was the framing biased toward the recommendation?
- Are the criteria for comparison explicit and appropriate for the user's context?
- Are downsides of the recommended option clearly stated, not minimized?
- Are upsides of the rejected options acknowledged?
- Could the decision be reversed later if it turns out wrong, or is it a one-way door?
- Is the recommendation backed by evidence or just by convention/preference?

## Analysis or Explanation

- Is the analysis accurate and complete?
- Are there alternative interpretations not considered?
- Is the reasoning sound or are there logical gaps?
- Are assumptions stated clearly?

## Output format

Present your critique as a concise bulleted list under two headings:

### What's solid
- (brief points on what works well)

### What needs attention
- (specific issues, each with a concrete suggestion for improvement)

If the output looks good, say so — but always find at least one thing to question. End by asking the user if they want you to address any of the identified issues.
