# Ideas

Captured directions for jwdb — not commitments, and not yet openspec proposals.

## The testing ideas, in my words

Start exercising the db through an external client, some kind of CLI right now before
adding any more features around distributed-ness. Do the testing with docker containers
and docker-compose. One of my big ideas at work right now is that everyone struggles to
get an environment up so that they can run tests locally; doing that with a Makefile
`make test` that does all the docker-compose work to make sure a container has all the
test dependencies makes life so much easier. Third, add static analysis. I'm sure Kotlin
can either use Kotlin-specific or JVM-specific static analysis, so add them now, early,
and benefit from them.

What follows is commentary on each.

## 1. External client / CLI before distributed features

This is the right sequencing and not just for testing convenience. Forcing yourself to
drive the db from outside means you have to commit to an actual interface boundary — a
protocol, a request/response shape, error semantics — while it's still cheap to change.
Distribution makes every one of those decisions ten times more expensive to revise. You'd
also stop testing internals-through-internals, which is the thing that quietly makes db
test suites impossible to refactor later.

Related: `openspec/changes/add-smart-client-library`.

## 2. `make test` that stands up docker-compose with all the deps

Notice what this one actually is: it's your work opinion, and you're using jwdb as the
place to prove it out. You said everyone at work struggles to get a local environment up
to run tests — so building the clean version of that on a project you fully control gives
you both a better jwdb and a concrete, demonstrable answer to a real organizational
problem. That's the same compounding you noticed with the AI tools, running the other
direction. Worth knowing you're doing it.

## 3. Static analysis early

Yes, and early is the whole trick — retrofitting it onto a mature codebase means 4,000
findings and you turn it off. For Kotlin the standard pairing is detekt (actual static
analysis — complexity, code smells, potential bugs) and ktlint (formatting/style, ends all
formatting debate with yourself). Both plug into Gradle cleanly, both can go behind the
same make target so they run the same way locally and in CI. The JVM-generic tools
(SpotBugs, Error Prone) are mostly Java-shaped and less worth it here. If you want a
stretch one later, Konsist lets you write architecture rules as tests — "nothing in
storage may import from query" — which is exactly the kind of boundary a db codebase wants
enforced mechanically.

## 4. Rename test tags to Google-style small / medium / large

Decided 2026-08-18. `openspec/project.md` has always specified Google-style small/medium/
large, but the code went with `unit` / `integration` / `e2e` — 13 tagged tests, three
`includeTags` blocks in `build.gradle.kts`, three make targets, and
`docs/TEST_CLASSIFICATION.md`. The spec is what's correct; the code should move to match.

The rename touches: `@Tag` annotations in `src/test`, `includeTags` in `build.gradle.kts`,
the `test-unit` / `test-integration` / `test-e2e` make targets, `docs/TEST_CLASSIFICATION.md`,
and any CI workflow that names a task. Worth doing before item 2 (docker-compose `make test`)
so the new targets get the right names the first time, rather than being renamed right after.

Small/medium/large classifies by *dependencies and speed* rather than by test shape, which
is the actual point — a "unit" test that touches the clock or the filesystem is medium, and
the tag should say so.
