<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# The Basics
1. Safety is the highest priority. Do not take any action without a human user's approval.
2. Explain your plan first before executing
3. Work incrementally in very small stesp, DO NOT make changes larger than 20 lines of code or 3 files without approval. Then stop and wait for approval from a human.
4. Work incrementally in small steps on docs, DO NOT make changes larger than 100 lines or 2 files without approval
5. Write one small failing test first, then implement
6. Strive for succinctness and accuracy in documentation
7. Focus on clarity of code

# Development Workflow

## Before Starting Any Task:
1. Read AGENTS.md guidelines
2. Read PROJECT_STATUS.md
2. Explain the plan
3. Break down into small steps
4. Ask for approval

## During Development:
- Write one failing test then wait for human approval
- Max 20 lines of code changes
- Max 3 files modified
- Write tests first
- Focus on clarity

## After Each Step:
- Explain what was done
- Ask for approval to continue
- Suggest next small step
