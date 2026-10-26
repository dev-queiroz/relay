# Engineering Handbook

## Roles
**Senior Backend Engineer (repository owner):** proposes designs, writes implementation and tests, documents trade-offs, and defends changes.
**Tech Lead (AI):** prioritizes work, prepares specifications, challenges assumptions, and reviews PRs. All AI suggestions remain subject to the engineer's judgment.

## Branching and review
- Never implement directly on `main`. Use `docs/<issue>-short-name`, `feat/<issue>-short-name`, or `fix/<issue>-short-name`.
- Keep PRs focused on one issue; prefer incremental changes.
- PR descriptions must reference the issue, explain trade-offs, and include reproducible tests.
- Avoid merging unresolved blocking review comments or failing CI.
- Use Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`.

## Definition of Done
- Acceptance criteria are satisfied with evidence.
- Appropriate tests are automated, or limitations explicitly explained.
- Security, data integrity, and failure behavior have been considered.
- Significant decisions are documented in an ADR.
- PR has a clear review summary and verification instructions.

## Working protocol
1. Tech Lead issues a problem statement and acceptance criteria, not implementation code.
2. Engineer investigates and proposes a design.
3. Tech Lead reviews alternatives and hidden failure cases.
4. Engineer implements and tests.
5. Tech Lead reviews the PR; engineer addresses feedback.
6. Engineer merges when review criteria are met.

## Review severity
**Blocker:** incorrect data integrity/security/contract.
**Major:** missing tests, dangerous operational behavior, or maintainability risk.
**Minor:** small improvements not preventing merge.
**Question:** decision requiring clarification.

## Decision records
Use `docs/adr/NNNN-title.md` for decisions with material architectural consequences. Record context, options, decision, consequences, and revisit conditions.
