package miniscala

import miniscala.Ast.*
import miniscala.Unparser.unparse

import scala.io.StdIn
import scala.util.parsing.input.Position

/**
  * Interpreter for MiniScala.
  */
object Interpreter {

  sealed abstract class Val
  case class IntVal(v: Int) extends Val
  case class BoolVal(v: Boolean) extends Val
  case class FloatVal(v: Float) extends Val
  case class StringVal(v: String) extends Val
  case class TupleVal(vs: List[Val]) extends Val
  case class ClosureVal(params: List[FunParam], optrestype: Option[Type], body: Exp, env: Env, cenv: ClassEnv, defs: List[DefDecl]) extends Val
  case class RefVal(loc: Loc, opttype: Option[Type]) extends Val
  case class ObjRefVal(loc: Loc, opttype: Option[Type]) extends Val
  case class ObjectVal(members: Env) extends Val

  val unitVal: Val = TupleVal(Nil)

  case class Constructor(params: List[FunParam], body: BlockExp, env: Env, cenv: ClassEnv, classes: List[ClassDecl], srcpos: Position)

  type Env = Map[Id, Val]

  type ClassEnv = Map[Id, Constructor]

  type Sto = Map[Loc, Val]

  type Loc = Int

  def nextLoc(sto: Sto): Loc = sto.size

  val nullLoc: Loc = -1

  /**
    * Evaluates an expression.
    */
  def eval(e: Exp, env: Env, cenv: ClassEnv, sto: Sto): (Val, Sto) = e match {
    case IntLit(c) => (IntVal(c), sto)
    case BoolLit(c) => (BoolVal(c), sto)
    case FloatLit(c) => (FloatVal(c), sto)
    case StringLit(c) => (StringVal(c), sto)
    case NullLit() => (ObjRefVal(nullLoc, None), sto)
    case VarExp(x) =>
      (getValue(env.getOrElse(x, throw InterpreterError(s"Unknown identifier '$x'", e)), sto), sto)
    case BinOpExp(leftexp, AndAndBinOp(), rightexp) =>
      val (leftval, sto1) = eval(leftexp, env, cenv, sto)
      leftval match {
        case BoolVal(true) =>
          val (rightval, sto2) = eval(rightexp, env, cenv, sto1)
          rightval match {
            case BoolVal(_) => (rightval, sto2)
            case _ => throw InterpreterError(s"Type mismatch at '&&', unexpected value ${valueToString(rightval)}", rightexp)
          }
        case BoolVal(false) => (leftval, sto1)
        case _ => throw InterpreterError(s"Type mismatch at '&&', unexpected value ${valueToString(leftval)}", leftexp)
      }
    case BinOpExp(leftexp, OrOrBinOp(), rightexp) =>
      val (leftval, sto1) = eval(leftexp, env, cenv, sto)
      leftval match {
        case BoolVal(false) =>
          val (rightval, sto2) = eval(rightexp, env, cenv, sto1)
          rightval match {
            case BoolVal(_) => (rightval, sto2)
            case _ => throw InterpreterError(s"Type mismatch at '||', unexpected value ${valueToString(rightval)}", rightexp)
          }
        case BoolVal(true) => (leftval, sto1)
        case _ => throw InterpreterError(s"Type mismatch at '||', unexpected value ${valueToString(leftval)}", leftexp)
      }
    case BinOpExp(leftexp, op, rightexp) =>
      val (leftval, sto1) = eval(leftexp, env, cenv, sto)
      val (rightval, sto2) = eval(rightexp, env, cenv, sto1)
      op match {
        case PlusBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (IntVal(v1 + v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (FloatVal(v1 + v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (FloatVal(v1 + v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (FloatVal(v1 + v2), sto2)
            case (StringVal(v1), StringVal(v2)) => (StringVal(v1 + v2), sto2)
            case (StringVal(v1), IntVal(v2)) => (StringVal(v1 + v2.toString), sto2)
            case (StringVal(v1), FloatVal(v2)) => (StringVal(v1 + v2.toString), sto2)
            case (IntVal(v1), StringVal(v2)) => (StringVal(v1.toString + v2), sto2)
            case (FloatVal(v1), StringVal(v2)) => (StringVal(v1.toString + v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '+', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case MinusBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (IntVal(v1 - v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (FloatVal(v1 - v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (FloatVal(v1 - v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (FloatVal(v1 - v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '-', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case MultBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (IntVal(v1 * v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (FloatVal(v1 * v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (FloatVal(v1 * v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (FloatVal(v1 * v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '*', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case DivBinOp() =>
          if (rightval == IntVal(0) || rightval == FloatVal(0.0f))
            throw InterpreterError(s"Division by zero", op)
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (IntVal(v1 / v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (FloatVal(v1 / v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (FloatVal(v1 / v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (FloatVal(v1 / v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '/', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case ModuloBinOp() =>
          if (rightval == IntVal(0) || rightval == FloatVal(0.0f))
            throw InterpreterError(s"Division by zero", op)
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (IntVal(v1 % v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (FloatVal(v1 % v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (FloatVal(v1 % v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (FloatVal(v1 % v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '%', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case EqualBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), FloatVal(v2)) => (BoolVal(v1 == v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (BoolVal(v1 == v2), sto2)
            case _ => (BoolVal(leftval == rightval), sto2) // note: not supporting type promotion inside tuples
          }
        case LessThanBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (BoolVal(v1 < v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (BoolVal(v1 < v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (BoolVal(v1 < v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (BoolVal(v1 < v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '<', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case LessThanOrEqualBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => (BoolVal(v1 <= v2), sto2)
            case (FloatVal(v1), FloatVal(v2)) => (BoolVal(v1 <= v2), sto2)
            case (IntVal(v1), FloatVal(v2)) => (BoolVal(v1 <= v2), sto2)
            case (FloatVal(v1), IntVal(v2)) => (BoolVal(v1 <= v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '<=', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case MaxBinOp() =>
          (leftval, rightval) match {
            case (IntVal(v1), IntVal(v2)) => if (v1 < v2) (IntVal(v2), sto2) else (IntVal(v1), sto2)
            case (FloatVal(v1), FloatVal(v2)) => if (v1 < v2) (FloatVal(v2), sto2) else (FloatVal(v1), sto2)
            case (IntVal(v1), FloatVal(v2)) => if (v1 < v2) (FloatVal(v2), sto2) else (FloatVal(v1.toFloat), sto2)
            case (FloatVal(v1), IntVal(v2)) => if (v1 < v2) (FloatVal(v2.toFloat), sto2) else (FloatVal(v1), sto2)
            case _ => throw InterpreterError(s"Type mismatch at 'max', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case AndBinOp() =>
          (leftval, rightval) match {
            case (BoolVal(v1), BoolVal(v2)) => (BoolVal(v1 & v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '&', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case OrBinOp() =>
          (leftval, rightval) match {
            case (BoolVal(v1), BoolVal(v2)) => (BoolVal(v1 | v2), sto2)
            case _ => throw InterpreterError(s"Type mismatch at '|', unexpected values ${valueToString(leftval)} and ${valueToString(rightval)}", op)
          }
        case AndAndBinOp() | OrOrBinOp() => throw RuntimeException("'&&' and '||' should be handled elsewhere") // (unreachable case)
      }
    case UnOpExp(op, exp) =>
      val (expval, sto1) = eval(exp, env, cenv, sto)
      op match {
        case NegUnOp() =>
          expval match {
            case IntVal(v) => (IntVal(-v), sto1)
            case FloatVal(v) => (FloatVal(-v), sto1)
            case _ => throw InterpreterError(s"Type mismatch at '-', unexpected value ${valueToString(expval)}", op)
          }
        case NotUnOp() =>
          expval match {
            case BoolVal(v) => (BoolVal(!v), sto1)
            case _ => throw InterpreterError(s"Type mismatch at '!', unexpected value ${valueToString(expval)}", op)
          }
      }
    case IfThenElseExp(condexp, thenexp, elseexp) =>
      val (condval, sto1) = eval(condexp, env, cenv, sto)
      condval match {
        case BoolVal(true) => eval(thenexp, env, cenv, sto1)
        case BoolVal(false) => eval(elseexp, env, cenv, sto1)
        case _ => throw InterpreterError(s"Type mismatch at 'if', unexpected value ${valueToString(condval)}", condexp)
      }
    case b: BlockExp =>
      val (res, _, sto1) = evalBlock(b, env, cenv, sto)
      (res, sto1)
    case TupleExp(exps) =>
      var (vals, sto1) = (List[Val](), sto)
      for (exp <- exps) {
        val (v, sto2) = eval(exp, env, cenv, sto1)
        vals = v :: vals
        sto1 = sto2
      }
      (TupleVal(vals.reverse), sto1)
    case MatchExp(exp, cases) =>
      val (expval, sto1) = eval(exp, env, cenv, sto)
      expval match {
        case TupleVal(vs) =>
          var res: Option[(Val, Sto)] = None
          for (c <- cases) {
            if (vs.length == c.pattern.length) {
              var env1 = env
              for (xv <- c.pattern.zip(vs))
                env1 = env1 + xv
              res = Some(eval(c.exp, env1, cenv, sto1))
            }
          }
          res match {
            case Some(v) => v
            case None => throw InterpreterError(s"No case matches value ${valueToString(expval)}", e)
          }
        case _ => throw InterpreterError(s"Tuple expected at match, found ${valueToString(expval)}", e)
      }
    case CallExp(funexp, args) =>
      val (funval, sto1) = eval(funexp, env, cenv, sto)
      funval match {
        case ClosureVal(params, optrestype, body, declenv, declcenv, defs) =>
          val declenv1 = rebindDefs(declenv, declcenv, defs)
          val (declenv2, sto2) = evalArgs(args, params, env, sto1, cenv, declenv1, declcenv, e)
          val (res, sto3) = eval(body, declenv2, declcenv, sto2)
          checkValueType(res, getType(optrestype, declcenv), body)
          (res, sto3)
        case _ => throw InterpreterError(s"Type mismatch at call, expected function, found value ${valueToString(funval)}", e)
      }
    case LambdaExp(params, body) => (ClosureVal(params, None, body, env, cenv, List[DefDecl]()), sto)
    case AssignmentExp(x, exp) =>
      env.getOrElse(x, throw InterpreterError(s"Unknown identifier '$x'", e)) match {
        case RefVal(loc, opttype) =>
          val (val1, sto1) = eval(exp, env, cenv, sto)
          checkValueType(val1, opttype, e)
          (unitVal, sto1 + (loc -> val1))
        case _ => throw InterpreterError(s"Illegal assignment, '$x' is not mutable", e)
      }
    case WhileExp(cond, body) =>
      val (condval, sto1) = eval(cond, env, cenv, sto)
      condval match {
        case BoolVal(true) =>
          val (_, sto2) = eval(body, env, cenv, sto1)
          eval(e, env, cenv, sto2)
        case BoolVal(false) => (unitVal, sto1)
        case _ => throw InterpreterError(s"Type mismatch at 'while', unexpected value ${valueToString(condval)}", e)
      }
    case DoWhileExp(body, cond) =>
      val (_, sto1) = eval(body, env, cenv, sto)
      val (condval, sto2) = eval(cond, env, cenv, sto1)
      condval match {
        case BoolVal(true) => eval(e, env, cenv, sto2)
        case BoolVal(false) => (unitVal, sto2)
        case _ => throw InterpreterError(s"Type mismatch at 'do-while', unexpected value ${valueToString(condval)}", e)
      }
    case NewObjExp(klass, args) =>
      val c = cenv.getOrElse(klass, throw InterpreterError(s"Unknown class name '$klass'", e))
      val declcenv1 = rebindClasses(c.env, c.cenv, c.classes)
      val (declenv1, sto1) = evalArgs(args, c.params, env, sto, cenv, c.env, declcenv1, e)
      val (_, env1, sto2) = evalBlock(c.body, declenv1, declcenv1, sto1)
      val newloc = nextLoc(sto2)
      val objenv = (c.body.defs.map(d => d.fun -> env1(d.fun)) ++ c.body.vars.map(d => d.x -> env1(d.x)) ++ c.body.vals.map(d => d.x -> env1(d.x))).toMap
      val sto3 = sto2 + (newloc -> ObjectVal(objenv))
      (ObjRefVal(newloc, Some(DynamicClassType(c.srcpos))), sto3)
    case LookupExp(objexp, member) =>
      val (objval, sto1) = eval(objexp, env, cenv, sto)
      objval match {
        case ObjRefVal(loc, _) =>
          if (loc == nullLoc) throw InterpreterError(s"Null pointer error", e)
          sto1(loc) match {
            case ObjectVal(members) =>
              (getValue(members.getOrElse(member, throw InterpreterError(s"No such member: $member", e)), sto1), sto1)
            case _ => throw RuntimeException(s"Expected an object value") // (unreachable case)
          }
        case _ => throw InterpreterError(s"Base value of lookup is not a reference to an object: ${valueToString(objval)}", e)
      }
  }

  /**
    * Evaluates a declaration.
    */
  def eval(d: Decl, env: Env, cenv: ClassEnv, sto: Sto, b: BlockExp): (Env, ClassEnv, Sto) = d match {
    case ValDecl(x, opttype, exp) =>
      val (v, sto1) = eval(exp, env, cenv, sto)
      val ot = getType(opttype, cenv)
      checkValueType(v, ot, d)
      val env1 = env + (x -> v)
      (env1, cenv, sto1)
    case VarDecl(x, opttype, exp) =>
      val (v, sto1) = eval(exp, env, cenv, sto)
      val ot = getType(opttype, cenv)
      checkValueType(v, ot, d)
      val loc = nextLoc(sto1)
      val env1 = env + (x -> RefVal(loc, ot))
      val sto2 = sto1 + (loc -> v)
      (env1, cenv, sto2)
    case DefDecl(fun, params, optrestype, body) =>
      val env1 = env + (fun -> ClosureVal(params, optrestype, body, env, cenv, b.defs))
      (env1, cenv, sto)
    case ClassDecl(klass, params, body) =>
      val cenv1 = cenv + (klass -> Constructor(params, body, env, cenv, b.classes, d.pos))
      (env, cenv1, sto)
  }

  /**
    * Evaluates the given block.
    * Returns the resulting value, the updated environment after evaluating all declarations, and the latest store.
    */
  def evalBlock(b: BlockExp, env: Env, cenv: ClassEnv, sto: Sto): (Val, Env, Sto) = {
    var env1 = env
    var cenv1 = cenv
    var sto1 = sto
    for (d <- b.vals ++ b.vars ++ b.defs ++ b.classes) {
      val (env2, cenv2, sto2) = eval(d, env1, cenv1, sto1, b)
      env1 = env2
      cenv1 = cenv2
      sto1 = sto2
    }
    var v1 = unitVal
    for (e <- b.exps) {
      val (v2, sto2) = eval(e, env1, cenv1, sto1)
      v1 = v2
      sto1 = sto2
    }
    (v1, env1, sto1)
  }

  /**
    * Evaluates the arguments `args` in environment `env` with store `sto`,
    * extends the environment `declenv` with the new bindings, and
    * returns the extended environment and the latest store.
    */
  def evalArgs(args: List[Exp], params: List[FunParam], env: Env, sto: Sto, cenv: ClassEnv, declenv: Env, declcenv: ClassEnv, e: Exp): (Env, Sto) = {
    if (args.length != params.length) throw InterpreterError("Wrong number of arguments at call/new", e)
    var (env1, sto1) = (declenv, sto)
    for ((p, arg) <- params.zip(args) ) {
      val (argval, sto2) = eval(arg, env, cenv, sto1)
      checkValueType(argval, getType(p.opttype, declcenv), arg)
      env1 = env1 + (p.x -> argval)
      sto1 = sto2
    }
    (env1, sto1)
  }

  /**
    * Resolves reference values by looking up the referenced value in the store.
    */
  def getValue(v: Val, sto: Sto): Val = v match {
    case RefVal(loc, _) => sto(loc)
    case _ => v
  }

  /**
    * Rebinds `defs` in `env` to support recursive function declarations.
    */
  def rebindDefs(env: Env, cenv: ClassEnv, defs: List[DefDecl]): Env = {
    var env1 = env
    for (d <- defs)
      env1 = env1 + (d.fun -> ClosureVal(d.params, d.optrestype, d.body, env, cenv, defs))
    env1
  }

  /**
    * Rebinds `classes` in `cenv` to support recursive class declarations.
    */
  def rebindClasses(env: Env, cenv: ClassEnv, classes: List[ClassDecl]): ClassEnv = {
    var cenv1 = cenv
    for (d <- classes)
      cenv1 = cenv1 + (d.klass -> Constructor(d.params, d.body, env, cenv, classes, d.pos))
    cenv1
  }

  /**
    * Returns the type described by the type annotation `ot` (if present).
    * Class names are converted to proper types according to the class environment `cenv`.
    */
  def getType(ot: Option[Type], cenv: ClassEnv): Option[Type] = ot.map(t => {
    def getType(t: Type): Type = t match {
      case ClassNameType(klass) => DynamicClassType(cenv.getOrElse(klass, throw InterpreterError(s"Unknown class '$klass'", t)).srcpos)
      case IntType() | BoolType() | FloatType() | StringType() | NullType() => t
      case TupleType(ts) => TupleType(ts.map(getType))
      case FunType(paramtypes, restype) => FunType(paramtypes.map(getType), getType(restype))
      case _ => throw RuntimeException(s"Unexpected type $t")
    }
    getType(t)
  })

  /**
    * Checks whether value `v` has type `ot` (if present), generates runtime type error otherwise.
    */
  def checkValueType(v: Val, ot: Option[Type], n: AstNode): Unit = ot match {
    case Some(t) =>
      (v, t) match {
        case (IntVal(_), IntType()) |
             (BoolVal(_), BoolType()) |
             (FloatVal(_), FloatType()) |
             (IntVal(_), FloatType()) |
             (StringVal(_), StringType()) => // do nothing
        case (TupleVal(vs), TupleType(ts)) if vs.length == ts.length =>
          for ((vi, ti) <- vs.zip(ts))
            checkValueType(vi, Some(ti), n)
        case (ClosureVal(cparams, optcrestype, _, _, cenv, _), FunType(paramtypes, restype)) if cparams.length == paramtypes.length =>
          for ((p, t) <- cparams.zip(paramtypes))
            checkSubtype(Some(t), getType(p.opttype, cenv), n)
          checkSubtype(getType(optcrestype, cenv), Some(restype), n)
        case (ObjRefVal(loc, _), NullType()) if loc == nullLoc => // do nothing
        case (ObjRefVal(loc, _), DynamicClassType(_)) if loc == nullLoc => // do nothing
        case (ObjRefVal(_, Some(vd: DynamicClassType)), td: DynamicClassType) =>
          if (!subtype(vd, td))
            throw InterpreterError(s"Type mismatch: object of type ${unparse(vd)} does not match type ${unparse(td)}", n)
        case (_, ClassNameType(_)) =>
          throw RuntimeException(s"Unexpected type $t (missing call to 'getType'?)")
        case _ =>
          throw InterpreterError(s"Type mismatch: value ${valueToString(v)} does not match type ${unparse(t)}", n)
      }
    case None => // do nothing
  }

  /**
    * Checks whether `t1` is a subtype of `t2`.
    */
  def subtype(t1: Type, t2: Type): Boolean = (t1, t2) match {
    case (t1, t2) if t1 == t2 => true
    case (IntType(), FloatType()) => true
    case (NullType(), DynamicClassType(_)) => true
    case (TupleType(ts1), TupleType(ts2)) if ts1.length == ts2.length =>
      ts1.zip(ts2).forall(p => subtype(p._1, p._2))
    case (FunType(pt1, rt1), FunType(pt2, rt2)) if pt1.length == pt2.length =>
      pt1.zip(pt2).forall(p => subtype(p._2, p._1)) && subtype(rt1, rt2)
    case (ClassNameType(_), _) =>
      throw RuntimeException(s"Unexpected type $t1 (missing call to 'getType'?)")
    case (_, ClassNameType(_)) =>
      throw RuntimeException(s"Unexpected type $t2 (missing call to 'getType'?)")
    case _ => false
  }

  /**
    * Checks whether `ot1` is a subtype of `t2` (if present), generates runtime type error otherwise.
    */
  def checkSubtype(ot1: Option[Type], ot2: Option[Type], n: AstNode): Unit = (ot1, ot2) match {
    case (Some(t1), Some(t2)) =>
      if (!subtype(t1, t2)) throw InterpreterError(s"Type mismatch: type ${unparse(t1)} does not match type ${unparse(t2)}", n)
    case _ => // do nothing
  }

  /**
    * Converts a value to its string representation (for error messages).
    */
  def valueToString(v: Val): String = v match {
    case IntVal(c) => c.toString
    case FloatVal(c) => c.toString
    case BoolVal(c) => c.toString
    case StringVal(c) => c
    case TupleVal(vs) => vs.map(valueToString).mkString("(", ",", ")")
    case ClosureVal(params, _, exp, _, _, _) => // the resulting string ignores the result type annotation, the declaration environments, and the set of defs
      s"<(${params.map(unparse).mkString(",")}), ${unparse(exp)}>"
    case ObjRefVal(loc, _) if loc == nullLoc => "null"
    case ObjRefVal(loc, _) => s"object#$loc" // the resulting string ignores the type annotation
    case _ => throw RuntimeException(s"Unexpected value $v") // (unreachable case)
  }

  /**
    * Builds an initial environment, with a value for each free variable in the program.
    */
  def makeInitialEnv(program: Exp): Env = {
    var env = Map[Id, Val]()
    for (x <- Vars.freeVars(program)) {
      print(s"Please provide an integer value for the variable $x: ")
      env = env + (x -> IntVal(StdIn.readInt()))
    }
    env
  }

  /**
    * Prints message if option -trace is used.
    */
  def trace(msg: String): Unit =
    if (Options.trace)
      println(msg)

  /**
    * Exception thrown in case of MiniScala runtime errors.
    */
  class InterpreterError(msg: String, node: AstNode) extends MiniScalaError(s"Runtime error: $msg", node.pos)
}
