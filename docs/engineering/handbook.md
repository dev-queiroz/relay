# Engineering Handbook

## Fixed responsibilities
**Tech Lead (AI):** owns and writes all product specifications, architecture documents, ADRs, acceptance criteria, backlog organization and review feedback.
**Backend Engineer (repository owner):** writes application code, tests, build configuration, fixes review findings, and submits PRs. No quizzes or documentation assignments. Engineer may challenge a spec through PR discussion; Tech Lead updates documentation.

## Language and workflow
All engineering artifacts and review comments are written in English.

- Work one ready-for-dev task at a time. Start with #6.
- Never implement directly on `main`. Use `feat/<issue>-short-name` or `fix/<issue>-short-name`.
- Keep PRs focused on a single issue and link it with `Closes #N`.
- Prefer small independent changes with working tests.
- PR describes implementation, verification commands, failure coverage, and open concerns.
- Use Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`.

## Definition of Done
- Acceptance criteria met with reproducible proof.
- Appropriate unit/integration tests passed.
- Failure cases and data integrity addressed.
- No secrets, unrelated changes, or unsupported performance claims.
- Tech Lead review completed with no unresolved blockers or major concerns.

## Review levels
- **Blocker:** invalid contract, security, integrity or correctness.
- **Major:** significant missing tests, reliability risk, or maintainability.
- **Minor:** polish; may follow up separately.
- **Question:** clarification or trade-off.

## Documentation governance
The Tech Lead is responsible for maintaining `docs/` and technical decision records. The engineer should implement against the committed documentation, not author it as part of development tickets unless a code-adjacent README or API schema is explicitly part of implementation.

## Milestone workflow
M0 specifications are complete. M1 starts with #6 and advances by issue dependencies. M2+ remain roadmap until the first vertical slice passes verification.
