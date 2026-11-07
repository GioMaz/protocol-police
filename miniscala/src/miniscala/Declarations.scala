package miniscala

import miniscala.Ast.*
import miniscala.Declarations.*

import java.util
import scala.collection.mutable

/**
  * The declarations that the identifiers of a program refer to, found by Declarations.analyze.
  */
class Declarations private(private val declarations: util.IdentityHashMap[AstNode, Declaration],
                           private val patterns: util.IdentityHashMap[MatchCase, List[Declaration]]) {

  /**
    * The declaration that the given node refers to. The node is either a use of an identifier
    * (a variable, an assignment, a 'new', or a class name in a type annotation) or a declaration
    * itself (a val, var, def, class, or function parameter).
    */
  def apply(n: AstNode): Declaration =
    if (declarations.containsKey(n)) declarations.get(n)
    else throw RuntimeException(s"No declaration for $n")

  /**
    * The declarations of the identifiers that the given match case binds, in the order of its pattern.
    */
  def bound(c: MatchCase): List[Declaration] = patterns.get(c)
}

/**
  * Resolution of identifiers to the declarations they refer to.
  *
  * Only member names are left, since MiniScala looks members up in objects at run-time.
  */
object Declarations {

  /**
    * One declaration of an identifier: a val, var, def, class, or function parameter, an identifier
    * bound by a match case, or an identifier that is free in the program and declared by it. Two
    * declarations are the same only if they are the same object, never because they look alike.
    */
  class Declaration(val name: Id, val node: AstNode) {
    override def toString: String = name
  }

  /**
    * Resolves the identifiers of the given program.
    */
  def analyze(program: Exp): Declarations = {
    val resolver = Resolver(program)
    resolver.resolve(program, Map(), Map())
    Declarations(resolver.declarations, resolver.patterns)
  }

  /**
    * The declarations that the identifiers in scope refer to.
    */
  private type Env = Map[Id, Declaration]

  private class Resolver(program: Exp) {

    val declarations = util.IdentityHashMap[AstNode, Declaration]()

    val patterns = util.IdentityHashMap[MatchCase, List[Declaration]]()

    /**
      * The declarations of the identifiers that are free in the program.
      */
    private val free = mutable.Map[Id, Declaration]()

    def resolve(e: Exp, env: Env, cenv: Env): Unit = e match {
      case _: Literal =>
      case VarExp(x) => declarations.put(e, lookup(x, env))
      case BinOpExp(leftexp, _, rightexp) =>
        resolve(leftexp, env, cenv)
        resolve(rightexp, env, cenv)
      case UnOpExp(_, exp) => resolve(exp, env, cenv)
      case IfThenElseExp(condexp, thenexp, elseexp) =>
        resolve(condexp, env, cenv)
        resolve(thenexp, env, cenv)
        resolve(elseexp, env, cenv)
      case BlockExp(vals, vars, defs, classes, exps) =>
        var env1 = env
        for (d <- vals) {
          resolve(d.exp, env1, cenv)
          annotation(d.opttype, cenv)
          env1 = env1 + (d.x -> declare(d.x, d))
        }
        for (d <- vars) {
          resolve(d.exp, env1, cenv)
          annotation(d.opttype, cenv)
          env1 = env1 + (d.x -> declare(d.x, d))
        }
        // the functions may be mutually recursive, so they are all in scope in their bodies
        for (d <- defs)
          env1 = env1 + (d.fun -> declare(d.fun, d))
        for (d <- defs)
          function(d.params, d.optrestype, d.body, env1, cenv)
        // the classes may be mutually recursive as well, and they are declared after the functions
        var cenv1 = cenv
        for (d <- classes)
          cenv1 = cenv1 + (d.klass -> declare(d.klass, d))
        for (d <- classes)
          function(d.params, None, d.body, env1, cenv1)
        for (exp <- exps)
          resolve(exp, env1, cenv1)
      case TupleExp(exps) =>
        for (exp <- exps)
          resolve(exp, env, cenv)
      case MatchExp(exp, cases) =>
        resolve(exp, env, cenv)
        for (c <- cases) {
          val bound = c.pattern.map(x => Declaration(x, c))
          patterns.put(c, bound)
          resolve(c.exp, env ++ c.pattern.zip(bound), cenv)
        }
      case CallExp(funexp, args) =>
        resolve(funexp, env, cenv)
        for (arg <- args)
          resolve(arg, env, cenv)
      case LambdaExp(params, body) => function(params, None, body, env, cenv)
      case AssignmentExp(x, exp) =>
        declarations.put(e, lookup(x, env))
        resolve(exp, env, cenv)
      case WhileExp(cond, body) =>
        resolve(cond, env, cenv)
        resolve(body, env, cenv)
      case DoWhileExp(body, cond) =>
        resolve(body, env, cenv)
        resolve(cond, env, cenv)
      case NewObjExp(klass, args) =>
        declarations.put(e, lookupClass(klass, cenv, e))
        for (arg <- args)
          resolve(arg, env, cenv)
      case LookupExp(objexp, _) => resolve(objexp, env, cenv)
    }

    /**
      * Resolves the identifiers in the parameters, the result type, and the body of a function or
      * a class.
      */
    private def function(params: List[FunParam], optrestype: Option[Type], body: Exp, env: Env, cenv: Env): Unit = {
      var env1 = env
      for (p <- params) {
        annotation(p.opttype, cenv)
        env1 = env1 + (p.x -> declare(p.x, p))
      }
      annotation(optrestype, cenv)
      resolve(body, env1, cenv)
    }

    /**
      * Resolves the class names in a type annotation, if there is one.
      */
    private def annotation(opttype: Option[Type], cenv: Env): Unit =
      for (t <- opttype)
        annotation(t, cenv)

    private def annotation(t: Type, cenv: Env): Unit = t match {
      case ClassNameType(k) => declarations.put(t, lookupClass(k, cenv, t))
      case TupleType(ts) =>
        for (t2 <- ts)
          annotation(t2, cenv)
      case FunType(paramtypes, restype) =>
        for (t2 <- paramtypes)
          annotation(t2, cenv)
        annotation(restype, cenv)
      case _ =>
    }

    /**
      * Declares an identifier, and remembers the declaration of the declaring node itself.
      */
    private def declare(x: Id, n: AstNode): Declaration = {
      val d = Declaration(x, n)
      declarations.put(n, d)
      d
    }

    /**
      * The declaration of an identifier. An identifier that is not declared anywhere is free in the
      * program, which declares it.
      */
    private def lookup(x: Id, env: Env): Declaration =
      env.getOrElse(x, free.getOrElseUpdate(x, Declaration(x, program)))

    private def lookupClass(k: Id, cenv: Env, n: AstNode): Declaration =
      cenv.getOrElse(k, throw ScopeError(s"Unknown class '$k'", n))
  }

  /**
    * Exception thrown when an identifier is used where it is not declared.
    */
  class ScopeError(msg: String, node: AstNode) extends MiniScalaError(msg, node.pos)
}
