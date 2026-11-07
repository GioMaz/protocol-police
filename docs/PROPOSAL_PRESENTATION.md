---
title: "Protocol Police"
subtitle: "Extensible typestate verification for MiniScala"
author: "Filippo Nardon, Giovanni Maria Zanchetta"
date: "November 9, 2026"

theme: metropolis
aspectratio: 169
fontsize: 10pt
slide-level: 2
bibliography: references.bib
link-citations: true
header-includes:
  - \usepackage{stmaryrd}
  - \DeclareUnicodeCharacter{03BC}{\ensuremath{\mu}}
---

## The problem: usage protocols

Many APIs only work if their operations are called **in the right order**:

- a file is opened, then read, then closed
- a transaction is begun, then ends in a commit or a rollback

These rules usually live only in the documentation. Breaking them causes
leaked resources, reads on closed streams and half-finished transactions.

**Protocol Police** is a static analyzer for MiniScala that checks whether a
program follows these protocols:

- whoever writes a class declares its protocol with **annotations**
- the analyzer checks every use of that class in the whole program
- a new protocol only needs a new annotated class, with no changes to the analyzer

## Annotated protocols

:::: columns
::: {.column width="58%"}
\scriptsize

```scala
@protocol(init = Idle, final = {Idle, Committed})
class Transaction() {
  @from(Idle) @to { case _ => Active }
  def begin(): Unit = ()
  @from(Active) @to { case 0 => Committed;
    case n if n > 0 => Active; case _ => Failed }
  def commit(): Int = 0
  @from(Active) @to { case _ => Idle }
  @from(Failed) @to { case _ => Idle }
  def rollback(): Unit = ()
}
```
```scala
val status = t.commit();
if (status == 0) { /* t is Committed */ }
else t.rollback()  // t is Active or Failed
```

\normalsize
:::
::: {.column width="40%"}
**Two checks**

- every call happens in a state that allows it
- at the end, every object is in a final state

**Three verdicts** (conservative analysis)

- *verified*: holds on all executions
- *error*: the violation is certain
- *warning*: a violation cannot be ruled out

**Path sensitivity**: link return values to states, so the `else` branch above
gives no false warning
:::
::::

## Techniques and related work

:::: columns
::: {.column width="50%"}
**Typestate checking**

- existing tools (Fugue, Plural) are *modular*: every client needs annotations
- we analyze the *whole program*: only protocol classes are annotated
- protocols kept separate from the analysis engine
- precision depends on paths and aliases
:::
::: {.column width="50%"}
**From the course**

- monotone frameworks
- interprocedural analysis (context sensitivity)
- points-to analysis
- path sensitivity

Goal: **combine them** into one working verifier
:::
::::

## Adapting MiniScala

:::: columns
::: {.column width="55%"}
We reuse the parser, type checker, interpreter, IR and CFG, and add:

1. **Annotations** in the lexer, parser and AST; the type checker rejects
   unknown states, the interpreter ignores them
2. **`import "path"`**: files with only classes, loaded once, no cycles;
   source positions record the file name
3. **Stub libraries** (`File`, `Transaction`, ...) that only model the API

Scope: core language first, then loops, functions and aliasing.
Closures are a stretch goal.
:::
::: {.column width="45%"}
```scala
import "lib/file.s"
{
  val f = new File("data.txt");
  f.open();
  f.read()
  // error: f definitely ends Open,
  // which is not a final state
}
```
:::
::::

## Plan, evaluation and method

:::: columns
::: {.column width="48%"}
\footnotesize

| Period       | Milestone                                  |
|--------------|--------------------------------------------|
| Nov 6–12     | Front end, `File` stub, intraproc. check   |
| Nov 13–19    | Aliases, loops, calls, contexts            |
| Nov 20–26    | Path sensitivity, transactions             |
| Nov 27–Dec 4 | Evaluation, presentation, report           |

\normalsize

**Method**: agile iterations (BMad), pair programming; AI for literature,
code and tests, always reviewed against results we work out by hand
:::
::: {.column width="48%"}
**Benchmarks**: files, transactions, maybe sockets; small programs, each
testing one feature, plus a few larger ones

**Questions**

- *Extensibility*: new protocols through annotations alone?
- *Precision*: verified, errors, false warnings; never verify a wrong program
- *Cost*: time and memory as programs grow
- With and without context and path sensitivity
:::
::::
