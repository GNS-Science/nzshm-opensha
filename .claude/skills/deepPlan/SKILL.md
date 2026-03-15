---
name: deepPlan
description: Interactive creation of implementation-ready plans or high-level epics through structured phases
user_invocable: true
---

# /deepPlan — Plan Creator

You are guiding the user through creating either an **epic** (a high-level roadmap of steps/features, each scoped enough for its own implementation plan) or an **implementation plan** (concrete code changes ready to execute). The track is determined during Phase 1.

Think hard during each phase, and use the full context of the codebase and the user's stated goal to inform your output. The quality of the final plan depends on the thoroughness of each phase.

**IMPORTANT: Do not skip ahead or combine phases. Complete each phase fully, present your output, and wait for user confirmation before moving to the next phase — unless the user explicitly tells you to proceed.**

## Pre-flight Check

If plan mode is not currently active, tell the user:

> Plan mode is not active. Please switch to plan mode (`Shift+Tab` or `/plan-mode`) before we continue — this keeps the plan in a dedicated document and enforces read-only safety.

Wait for the user to confirm plan mode is active before proceeding.

## Trivial Task Guard

If the user's goal is a single, well-understood change to 1–2 files with no design decisions needed, suggest:

> This looks straightforward enough to implement directly without a full plan. Want to proceed without /deepPlan, or do you still want the structured planning?

If they want to proceed without, exit. Otherwise continue.

## Process

Work through the phases below **interactively** — complete each phase and get user confirmation before moving to the next.

This skill works within plan mode. Use `Agent` with `subagent_type: Explore` for codebase exploration and `subagent_type: Plan` for design work, so you don't flood the main context.

---

### Phase 1: Understanding

1. **Check for existing plans**: Search `.claude/plans/` for related prior plans. If found, surface them and ask if they're still relevant.
2. **Summarise** the user's stated goal in 2–3 sentences.
3. **Classify scope** — Evaluate whether the goal is epic-level or implementation-level:
   - Goal spans multiple independent features or subsystems → epic
   - No single PR could deliver the full goal → epic
   - Goal targets a specific, well-bounded change → implementation

   Present the classification with brief reasoning.
4. **Evaluate goal completeness** — probe for missing complementary concerns:
   - Backup → recovery?
   - Write path → read path?
   - API endpoint → error responses, auth, rate limiting?
   - Data model → migration, backwards compatibility?
   - New feature → how to disable/configure?
   - Constraint → what validates it?
   - New class → tests?
   - CLI/entry point → help text, error messages?
   - Config → defaults, validation, documentation?

   Present any identified gaps as suggestions.
5. **Ask** up to 5 clarifying questions (fewer if things are clear). Include:
   - Confirmation of the classification
   - Questions arising from any completeness gaps found above
6. **Wait** for answers before proceeding.

**Routing:**
- If classified as **epic** → proceed to Phase 2E.
- If classified as **implementation** → proceed to Phase 2.

---

### Phase 2E (Epic): Exploration & Decomposition

1. **Launch up to 3 Explore agents in parallel** to scan the codebase areas most likely affected. Each agent should have a distinct focus area.
2. Based on exploration results, break the goal into ordered steps/features, each scoped enough for a single implementation plan.

For each step provide:
- **Name** — short descriptive title
- **Goal** — 1–2 sentences on what this step achieves
- **Scope** — key files/classes affected, preliminary technical direction
- **Complexity** — S / M / L (relative sizing)
- **Upstream changes** — whether changes to ../opensha are needed
- **Dependencies** — which other steps must come first
- **Acceptance criteria** — how to know the step is done

**Incremental delivery**: Order steps so the system is in a working state after each one. Identify the smallest shippable increment.

Present to the user for feedback before proceeding.

---

### Phase 3E (Epic): Epic Plan Draft & Review

Write the epic plan, then **self-review before presenting**:
- Are the steps correctly ordered and scoped?
- Are dependencies captured? Are there gaps between steps?
- Does the set of steps fully cover the goal?
- For the recommended approach: what are 2 alternatives and why were they rejected?
- What is the riskiest assumption?

**Plan sections:**
1. **Context** — Overall goal, what prompted it, intended outcome.
2. **Steps** — Ordered list with per-step details from Phase 2E.
3. **Alternatives Considered** — Briefly, why this decomposition over others.
4. **Open Questions** — Unknowns that need resolution.
5. **Risks** — What could go wrong, riskiest assumption called out.

Note in the plan that each step can be turned into a concrete `/deepPlan` invocation.

Present the plan. Revise based on user feedback, then write the final version to the plan mode file and call `ExitPlanMode`.

---

### Phase 2 (Implementation): Discovery & Design

1. **Launch up to 3 Explore agents in parallel** to investigate:
   - All affected files, classes, and methods
   - Existing patterns, conventions, and data flows to follow
   - Existing test coverage in affected areas
2. Present a concise summary of exploration findings. Flag surprises or ambiguities — ask follow-up questions if needed.
3. Once findings are confirmed, **launch 1 Plan agent** to design the implementation. Provide it with:
   - The user's goal and all clarifications
   - Exploration findings (files, patterns, conventions)
   - Instruction to enumerate at least 2 alternative approaches and recommend one with rationale
   - Instruction to identify the riskiest assumption

**Wait** for user confirmation before proceeding.

---

### Phase 3 (Implementation): Plan Draft & Review

Write the plan, incorporating the Plan agent's design. **Self-review before presenting** using this checklist:
- [ ] Every step traces back to the stated goal (no scope creep)
- [ ] All affected files identified
- [ ] APIs fully specified (inputs, outputs, errors)
- [ ] Error handling covered
- [ ] Test coverage adequate
- [ ] Steps in logical order
- [ ] Downstream/upstream impact assessed

**Plan sections:**
1. **Context** — What is being done, why, what prompted it.
2. **Design Decisions** — Key choices, rationale, rejected alternatives, riskiest assumption.
3. **Implementation Steps** — Ordered list. Each step includes:
   - **What**: the concrete change
   - **How**: approach, referencing specific files/classes
   - **Why**: rationale
   - **Complexity**: S / M / L
4. **New Classes/Interfaces** — For each: FQN, responsibility, key method signatures, relationships.
5. **Modified Classes** — What changes, impact on signatures/behaviour.
6. **Configuration/Resources** — New or modified config/resource/build files.
7. **Test Plan** — New tests, existing tests to update, edge cases.
8. **Incremental Delivery** — Smallest shippable increment, suggested PR boundaries.
9. **Migration/Compatibility** — Breaking changes and mitigation. If none, say so.

Present the plan. Revise based on user feedback, then write the final version to the plan mode file and call `ExitPlanMode`.
