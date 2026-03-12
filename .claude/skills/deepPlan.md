---
name: deepPlan
description: Interactive creation of complete, implementation-ready plans through structured phases
user_invocable: true
---

# /deepPlan — Implementation Plan Creator

You are guiding the user through creating a complete, implementation-ready plan. The plan must be detailed enough that another developer or Claude session can pick it up and execute without further clarification.

## Pre-flight Check

If plan mode is not currently active, tell the user:

> Plan mode is not active. Please switch to plan mode (`Shift+Tab` or `/plan-mode`) before we continue — this keeps the plan in a dedicated document.

Wait for the user to confirm plan mode is active before proceeding.

## Process

Work through the phases below **interactively** — complete each phase and get user confirmation before moving to the next. Use the `Agent` tool (subagent_type: `Explore`) for codebase exploration so you don't flood the main context.

---

### Phase 1: Understanding

1. **Summarise** the user's stated goal in 2–3 sentences.
2. **Light codebase exploration**: Do a quick scan of the areas most likely affected. Check for existing patterns, complementary systems, or conventions that are relevant. This grounds the next step.
3. **Evaluate goal completeness** — explicitly probe for missing complementary concerns. Consider gaps like:
   - Backup → recovery?
   - Write path → read path?
   - API endpoint → error responses, auth, rate limiting?
   - Data model → migration, backwards compatibility?
   - New feature → how to disable/configure?
   - Constraint → what validates it?
   - New class → tests?
   - CLI/entry point → help text, error messages?
   - Config → defaults, validation, documentation?
4. **Present** any identified gaps as suggestions, informed by what the codebase already has.
5. **Ask** up to 5 clarifying questions (fewer if things are clear).
6. **Wait** for answers before proceeding to Phase 2.

---

### Phase 2: Discovery

Explore the codebase in depth:

1. Identify all affected files, classes, and methods.
2. Note existing patterns, conventions, and data flows that the implementation should follow.
3. Note existing test coverage in the affected areas.
4. Flag any surprises or ambiguities — ask follow-up questions if needed before proceeding.

Present a concise summary of findings to the user.

---

### Phase 3: Plan Draft

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

### Phase 4: Evaluation

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

### Phase 5: Finalisation

1. Produce the clean, final plan as the plan mode document.
2. Propose a descriptive filename (e.g. `joint-inversion-mfd-constraints.md`) and ask the user to confirm or rename.
3. Save a copy to `docs/plans/<confirmed-name>.md` for long-term project documentation.
4. Confirm with the user that the plan is complete.

The saved plan must be **self-contained** — anyone reading it should understand the goal, context, decisions, and every implementation step without needing this conversation.
