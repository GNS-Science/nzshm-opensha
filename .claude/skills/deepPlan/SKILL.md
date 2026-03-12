---
name: deepPlan
description: Interactive creation of implementation-ready plans or high-level epics through structured phases
user_invocable: true
---

# /deepPlan — Plan Creator

You are guiding the user through creating either an **epic** (a high-level roadmap of steps/features, each scoped enough for its own implementation plan) or an **implementation plan** (concrete code changes ready to execute). The track is determined during Phase 1.

**IMPORTANT: Do not skip ahead or combine phases. Complete each phase fully, present your output, and wait for user confirmation before moving to the next phase — unless the user explicitly tells you to proceed.**

**Think hard and deeply at every phase.** Take time to reason thoroughly about the goal, the codebase, trade-offs, and potential issues before presenting your output.

## Pre-flight Check

If plan mode is not currently active, tell the user:

> Plan mode is not active. Please switch to plan mode (`Shift+Tab` or `/plan-mode`) before we continue — this keeps the plan in a dedicated document.

Wait for the user to confirm plan mode is active before proceeding.

## Process

Work through the phases below **interactively** — complete each phase and get user confirmation before moving to the next. Use the `Agent` tool (subagent_type: `Explore`) for codebase exploration so you don't flood the main context.

---

### Phase 1: Understanding

1. **Summarise** the user's stated goal in 2–3 sentences.
2. **Classify scope** — Evaluate whether the goal is epic-level or implementation-level using these heuristics:
   - Goal spans multiple independent features or subsystems → epic
   - No single PR could deliver the full goal → epic
   - Goal is described in terms of outcomes rather than specific changes → epic
   - Multiple phases of work with dependencies between them → epic
   - Goal targets a specific, well-bounded change → implementation

   Present the classification with brief reasoning. Ask the user to confirm or override.
3. **Light codebase exploration**: Do a quick scan of the areas most likely affected. Check for existing patterns, complementary systems, or conventions that are relevant. This grounds the next step.
4. **Evaluate goal completeness** — explicitly probe for missing complementary concerns. Consider gaps like:
   - Backup → recovery?
   - Write path → read path?
   - API endpoint → error responses, auth, rate limiting?
   - Data model → migration, backwards compatibility?
   - New feature → how to disable/configure?
   - Constraint → what validates it?
   - New class → tests?
   - CLI/entry point → help text, error messages?
   - Config → defaults, validation, documentation?
5. **Present** any identified gaps as suggestions, informed by what the codebase already has.
6. **Ask** up to 5 clarifying questions (fewer if things are clear).
7. **Wait** for answers before proceeding.

**Routing:**
- If classified as **epic** → proceed to Phase 2E.
- If classified as **implementation** → proceed to Phase 2.
- If the goal appears mixed → ask the user to narrow scope or pick one track before proceeding.

---

### Phase 2E (Epic): Decomposition

Break the goal into ordered steps/features, each scoped enough for a single implementation plan.

For each step provide:
- **Name** — short descriptive title
- **Goal** — 1–2 sentences on what this step achieves
- **Scope** — areas of the codebase likely affected, preliminary technical direction
- **Dependencies** — which other steps must come first
- **Acceptance criteria** — how to know the step is done

Present to the user for feedback before proceeding.

---

### Phase 3E (Epic): Epic Plan Draft

Write the epic plan with these sections:

1. **Summary** — Overall goal and why it's being pursued.
2. **Steps** — Ordered list with the per-step details from Phase 2E.
3. **Open Questions** — Unknowns that need resolution before or during implementation.
4. **Risks** — What could go wrong at the epic level.

Note in the plan that each step can be turned into a concrete `/deepPlan` invocation.

---

### Phase 4E (Epic): Evaluation

Critically self-review the epic plan:

- Are the steps correctly ordered and scoped?
- Are dependencies between steps captured?
- Does the set of steps fully cover the overall goal?
- Are there gaps — things that fall between steps?
- Are the acceptance criteria clear enough to know when each step is done?

Revise the plan based on findings. Present the evaluation summary to the user.

---

### Phase 5E (Epic): Finalisation

1. Produce the clean, final epic plan as the plan mode document.
2. Propose a descriptive filename and ask the user to confirm or rename.
3. Save a copy to `docs/plans/<confirmed-name>.md` for long-term project documentation.
4. Note in the document that this is an epic — each step may spawn its own `/deepPlan`.
5. Confirm with the user that the plan is complete.

The saved plan must be **self-contained** — anyone reading it should understand the overall goal, the decomposition, and the scope of each step without needing this conversation.

---

### Phase 2 (Implementation): Discovery

Explore the codebase in depth:

1. Identify all affected files, classes, and methods.
2. Note existing patterns, conventions, and data flows that the implementation should follow.
3. Note existing test coverage in the affected areas.
4. Flag any surprises or ambiguities — ask follow-up questions if needed before proceeding.

Present a concise summary of findings to the user.

---

### Phase 3 (Implementation): Plan Draft

Write the plan with these sections:

1. **Summary** — What is being done and why.
2. **Design Decisions** — Key choices made, rationale, and rejected alternatives.
3. **Implementation Steps** — Ordered list. Each step includes:
   - **What**: the concrete change
   - **How**: approach, referencing specific files/classes
   - **Why**: rationale for this step
4. **New Classes/Interfaces** — For each:
   - Fully qualified name
   - Responsibility (one sentence)
   - Key methods with signatures
   - Relationships to existing classes
5. **Modified Classes** — For each:
   - What changes
   - Impact on signatures or behaviour
6. **Configuration/Resources** — New or modified config files, resource files, build changes.
7. **Test Plan** — New tests to write, existing tests to update, edge cases to cover.
8. **Migration/Compatibility** — Breaking changes and how they are mitigated. If none, say so.

---

### Phase 4 (Implementation): Evaluation

Critically self-review the draft plan:

- **Goal Alignment**: Does every step trace back to the stated goal? Is there scope creep? Will the end state fully achieve the goal?
- **Completeness Checklist**:
  - [ ] All files identified
  - [ ] APIs fully specified (inputs, outputs, errors)
  - [ ] Error handling covered
  - [ ] Test coverage adequate
  - [ ] Dependencies identified
  - [ ] Steps in logical order
- **Impact Analysis**: Downstream consumers, upstream dependencies, side effects on unrelated code, performance implications.
- **Risks and Unknowns**: What could go wrong, assumptions that might not hold, areas that may need prototyping first.

Revise the plan based on findings. Present the evaluation summary to the user.

---

### Phase 5 (Implementation): Finalisation

1. Produce the clean, final plan as the plan mode document.
2. Propose a descriptive filename (e.g. `joint-inversion-mfd-constraints.md`) and ask the user to confirm or rename.
3. Save a copy to `docs/plans/<confirmed-name>.md` for long-term project documentation.
4. Confirm with the user that the plan is complete.

The saved plan must be **self-contained** — anyone reading it should understand the goal, context, decisions, and every implementation step without needing this conversation.
