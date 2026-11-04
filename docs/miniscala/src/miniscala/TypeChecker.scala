package miniscala

import miniscala.Ast.*
import miniscala.Unparser.unparse

/**
  * Type checker for MiniScala.
  */
object TypeChecker {

  type TypeEnv = Map[Id, Type]

  type ClassTypeEnv = Map[Id, StaticClassType]

  val unitType: Type = TupleType(Nil)

  def typeCheck(e: Exp, tenv: TypeEnv, ctenv: ClassTypeEnv): Type = e match {
    case IntLit(_) => IntType()
    case BoolLit(_) => BoolType()
    case FloatLit(_) => FloatType()
    case StringLit(_) => StringType()
    case NullLit() => NullType()
    case VarExp(x) => tenv.getOrElse(x, throw TypeError(s"Unknown identifier '$x'", e)) match {
      case MutableType(thetype) => thetype
      case t: Type => t
    }
    case BinOpExp(leftexp, op, rightexp) =>
      val lefttype = typeCheck(leftexp, tenv, ctenv)
      val righttype = typeCheck(rightexp, tenv, ctenv)
      op match {
        case PlusBinOp() =>
          (lefttype, righttype) match {
            case (IntType(), IntType()) => IntType()
            case (FloatType(), FloatType()) => FloatType()
            case (IntType(), FloatType()) => FloatType()
            case (FloatType(), IntType()) => FloatType()
            case (StringType(), StringType()) => StringType()
            case (StringType(), IntType()) => StringType()
            case (StringType(), FloatType()) => StringType()
            case (IntType(), StringType()) => StringType()
            case (FloatType(), StringType()) => StringType()
            case _ => throw TypeError(s"Type mismatch at '+', unexpected types ${unparse(lefttype)} and ${unparse(righttype)}", op)
          }
        case MinusBinOp() | MultBinOp() | DivBinOp() | ModuloBinOp() | MaxBinOp() =>
          (lefttype, righttype) match {
            case (IntType(), IntType()) => IntType()
            case (FloatType(), FloatType()) => FloatType()
            case (IntType(), FloatType()) => FloatType()
            case (FloatType(), IntType()) => FloatType()
            case _ => throw TypeError(s"Type mismatch at '${unparse(op)}', unexpected types ${unparse(lefttype)} and ${unparse(righttype)}", op)
          }
        case EqualBinOp() =>
          BoolType()
        case LessThanBinOp() | LessThanOrEqualBinOp() =>
          (lefttype, righttype) match {
            case (IntType(), IntType()) => BoolType()
            case (FloatType(), FloatType()) => BoolType()
            case (IntType(), FloatType()) => BoolType()
            case (FloatType(), IntType()) => BoolType()
            case _ => throw TypeError(s"Type mismatch at '${unparse(op)}', unexpected types ${unparse(lefttype)} and ${unparse(righttype)}", op)
          }
        case AndBinOp() | OrBinOp() | AndAndBinOp() | OrOrBinOp() =>
          (lefttype, righttype) match {
            case (BoolType(), BoolType()) => BoolType()
            case _ => throw TypeError(s"Type mismatch at '${unparse(op)}', unexpected types ${unparse(lefttype)} and ${unparse(righttype)}", op)
          }
      }
    case UnOpExp(op, exp) =>
      val exptype = typeCheck(exp, tenv, ctenv)
      op match {
        case NegUnOp() =>
          exptype match {
            case IntType() => IntType()
            case FloatType() => FloatType()
            case _ => throw TypeError(s"Type mismatch at '-', unexpected type ${unparse(exptype)}", op)
          }
        case NotUnOp() =>
          exptype match {
            case BoolType() => BoolType()
            case _ => throw TypeError(s"Type mismatch at '!', unexpected type ${unparse(exptype)}", op)
          }
      }
    case IfThenElseExp(condexp, thenexp, elseexp) =>
      val condtype = typeCheck(condexp, tenv, ctenv)
      if (condtype != BoolType())
        throw TypeError(s"Type mismatch at 'if', unexpected type ${unparse(condtype)}", condexp)
      val thentype = typeCheck(thenexp, tenv, ctenv)
      val elsetype = typeCheck(elseexp, tenv, ctenv)
      if (thentype != elsetype) // TODO: introduce type Any? (also, StaticClassType equals only needs to consider the srcpos field, not all fields)
        throw TypeError(s"Type mismatch at 'if', expected same type of branches, found types ${unparse(thentype)} and ${unparse(elsetype)}", e)
      thentype
    case BlockExp(vals, vars, defs, classes, exps) =>
      checkDuplicates(vals ++ vars ++ defs)
      checkDuplicates(classes)
      var tenv1 = tenv
      for (d <- vals) {
        val t = typeCheck(d.exp, tenv1, ctenv)
        val ot = getType(d.opttype, ctenv)
        checkSubtype(t, ot, d)
        tenv1 = tenv1 + (d.x -> ot.getOrElse(t))
      }
      for (d <- vars) {
        val t = typeCheck(d.exp, tenv1, ctenv)
        val ot = getType(d.opttype, ctenv)
        checkSubtype(t, ot, d)
        tenv1 = tenv1 + (d.x -> MutableType(ot.getOrElse(t)))
      }
      for (d <- defs)
        tenv1 = tenv1 + (d.fun -> getType(makeFunType(d), ctenv))
      for (d <- defs) {
        checkDuplicates(d.params)
        var tenv2 = tenv1
        for (p <- d.params)
          tenv2 = tenv2 + (p.x -> getType(p.opttype.getOrElse(throw TypeError(s"Type annotation missing at parameter ${p.x}", p)), ctenv))
        val exptype = typeCheck(d.body, tenv2, ctenv)
        checkSubtype(exptype, getType(d.optrestype, ctenv), d)
      }
      var ctenv1 = ctenv
      for (d <- classes)
        ctenv1 = ctenv1 + (d.klass -> makeStaticClassType(d, ctenv, classes))
      for (d <- classes) {
        checkDuplicates(d.params)
        var tenv2 = tenv1
        for (p <- d.params)
          tenv2 = tenv2 + (p.x -> getType(p.opttype.getOrElse(throw TypeError(s"Type annotation missing at parameter ${p.x}", p)), ctenv1))
        typeCheck(d.body, tenv2, ctenv1)
      }
      var res: Type = unitType
      for (exp <- exps)
        res = typeCheck(exp, tenv1, ctenv1)
      res
    case TupleExp(exps) => TupleType(exps.map(exp => typeCheck(exp, tenv, ctenv)))
    case MatchExp(exp, cases) =>
      val exptype = typeCheck(exp, tenv, ctenv)
      exptype match {
        case TupleType(ts) =>
          var res: Option[Type] = None
          for (c <- cases) {
            if (ts.length == c.pattern.length) {
              var tenv1 = tenv
              for (xt <- c.pattern.zip(ts))
                tenv1 = tenv1 + xt
              res = Some(typeCheck(c.exp, tenv1, ctenv))
            }
          }
          res match {
            case Some(t) => t
            case None => throw TypeError(s"No case matches type ${unparse(exptype)}", e)
          }
        case _ => throw TypeError(s"Tuple expected at match, found ${unparse(exptype)}", e)
      }
    case CallExp(funexp, args) =>
      val funexptype = typeCheck(funexp, tenv, ctenv)
      funexptype match {
        case FunType(paramtypes, restype) =>
          if (args.length != paramtypes.length)
            throw TypeError(s"Type mismatch at call, wrong number of arguments", e)
          for ((arg, param) <- args.zip(paramtypes)) {
            val argtype = typeCheck(arg, tenv, ctenv)
            checkSubtype(argtype, param, e)
          }
          restype
        case _ => throw TypeError(s"Type mismatch at call, expected function, found type ${unparse(funexptype)}", funexp)
      }
    case LambdaExp(params, body) =>
      val paramtypes = params.map(p =>
        getType(p.opttype.getOrElse(throw TypeError(s"Type annotation missing at parameter '${p.x}'", p)), ctenv)
      )
      val paramids = params.map(p => p.x)
      val restype = typeCheck(body, tenv ++ paramids.zip(paramtypes), ctenv)
      FunType(paramtypes, restype)
    case AssignmentExp(x, exp) =>
      tenv.getOrElse(x, throw TypeError(s"Unknown identifier '$x'", e)) match {
        case MutableType(t) =>
          val exptype = typeCheck(exp, tenv, ctenv)
          checkSubtype(exptype, t, e)
        case _ => throw TypeError(s"'$x' not mutable", e)
      }
      unitType
    case WhileExp(cond, body) =>
      val condtype = typeCheck(cond, tenv, ctenv)
      if (condtype != BoolType())
        throw TypeError(s"Type mismatch at 'while', expected boolean, found type ${unparse(condtype)}", cond)
      typeCheck(body, tenv, ctenv)
      unitType
    case DoWhileExp(body, cond) =>
      typeCheck(body, tenv, ctenv)
      val condtype = typeCheck(cond, tenv, ctenv)
      if (condtype != BoolType())
        throw TypeError(s"Type mismatch at 'do-while', expected boolean, found type ${unparse(condtype)}", cond)
      unitType
    case NewObjExp(klass, args) =>
      val t = ctenv.getOrElse(klass, throw TypeError(s"Unknown class '$klass'", e))
      if (args.length != t.params.length)
        throw TypeError(s"Type mismatch at new, wrong number of arguments", e)
      val declctenv = rebindClasses(t.ctenv, t.classes)
      for ((arg, param) <- args.zip(t.params)) {
        val argtype = typeCheck(arg, tenv, ctenv)
        val paramtype = getType(param, declctenv)
        checkSubtype(argtype, paramtype, e)
      }
      t
    case LookupExp(objexp, member) =>
      typeCheck(objexp, tenv, ctenv) match {
        case StaticClassType(_, _, membertypes, declctenv, classes) =>
          val declctenv1 = rebindClasses(declctenv, classes)
          getType(membertypes.getOrElse(member, throw TypeError(s"Unknown member '$member'", e)), declctenv1)
        case t => throw TypeError(s"Type mismatch at lookup, expected class type, found type ${unparse(t)}", e)
      }
  }

  /**
    * Returns the type described by the type annotation `t`.
    * Class names are converted to proper types according to the class type environment `ctenv`.
    */
  def getType(t: Type, ctenv: ClassTypeEnv): Type = t match {
    case ClassNameType(klass) => ctenv.getOrElse(klass, throw TypeError(s"Unknown class '$klass'", t))
    case IntType() | BoolType() | FloatType() | StringType() | NullType() => t
    case TupleType(ts) => TupleType(ts.map(tt => getType(tt, ctenv)))
    case FunType(paramtypes, restype) => FunType(paramtypes.map(tt => getType(tt, ctenv)), getType(restype, ctenv))
    case _ => throw RuntimeException(s"Unexpected type $t")
  }

  /**
    * Returns the type described by the optional type annotation `ot` (if present).
    */
  def getType(ot: Option[Type], ctenv: ClassTypeEnv): Option[Type] = ot.map(t => getType(t, ctenv))

  /**
    * Returns the function type for the function declaration `d`.
    */
  def makeFunType(d: DefDecl): FunType =
    FunType(d.params.map(p => p.opttype.getOrElse(throw TypeError(s"Type annotation missing at parameter ${p.x}", p))),
      d.optrestype.getOrElse(throw TypeError(s"Type annotation missing at function result ${d.fun}", d)))

  /**
    * Returns the class type for the class declaration `d`.
    */
  def makeStaticClassType(d: ClassDecl, ctenv: ClassTypeEnv, classes: List[ClassDecl]): StaticClassType = {
    var membertypes: TypeEnv = Map()
    for (m <- d.body.vals)
      membertypes = membertypes + (m.x -> m.opttype.getOrElse(throw TypeError(s"Type annotation missing at field ${m.x}", m)))
    for (m <- d.body.vars)
      membertypes = membertypes + (m.x -> m.opttype.getOrElse(throw TypeError(s"Type annotation missing at field ${m.x}", m)))
    for (m <- d.body.defs)
      membertypes = membertypes + (m.fun -> makeFunType(m))
    StaticClassType(d.pos, d.params.map(f => f.opttype.getOrElse(throw TypeError(s"Type annotation missing at parameter ${f.x}", d))), membertypes, ctenv, classes)
  }

  /**
    * Rebinds `classes` in `ctenv` to support recursive class declarations.
    */
  def rebindClasses(ctenv: ClassTypeEnv, classes: List[ClassDecl]): ClassTypeEnv = {
    var ctenv1 = ctenv
    for (d <- classes)
      ctenv1 = ctenv1 + (d.klass -> makeStaticClassType(d, ctenv, classes))
    ctenv1
  }

  /**
    * Checks whether `t1` is a subtype of `t2`.
    */
  def subtype(t1: Type, t2: Type): Boolean = (t1, t2) match {
    case (t1, t2) if t1 == t2 => true
    case (IntType(), FloatType()) => true
    case (StaticClassType(srcpos1, _, _, _, _), StaticClassType(srcpos2, _, _, _, _)) if srcpos1 == srcpos2 => true
    case (NullType(), StaticClassType(_, _, _, _, _)) => true
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
    * Checks whether `t1` is a subtype of `t2`, generates type error otherwise.
    */
  def checkSubtype(t1: Type, t2: Type, n: AstNode): Unit =
    if (!subtype(t1, t2)) throw TypeError(s"Type ${unparse(t1)} is not subtype of ${unparse(t2)}", n)

  /**
    * Checks whether `t1` is a subtype of `ot2` (if present), generates type error otherwise.
    */
  def checkSubtype(t: Type, ot2: Option[Type], n: AstNode): Unit = ot2 match {
    case Some(t2) => checkSubtype(t, t2, n)
    case None => // do nothing
  }

  /**
    * Builds an initial type environment, with a type for each free variable in the program.
    */
  def makeInitialTypeEnv(program: Exp): TypeEnv = {
    var tenv: TypeEnv = Map()
    for (x <- Vars.freeVars(program))
      tenv = tenv + (x -> IntType())
    tenv
  }

  /**
    * Checks whether `ns` contains multiple declarations of the same identifier.
    */
  def checkDuplicates(ns: List[AstNode]): Unit = {
    for (d <- ns.groupBy {
      case d: ValDecl => d.x
      case d: VarDecl => d.x
      case d: DefDecl => d.fun
      case d: ClassDecl => d.klass
      case p: FunParam => p.x
      case a => throw RuntimeException(s"Unexpected AST node $a")
    }.filter { case (_, gs) => gs.size > 1 })
      println(s"Warning: multiple occurrences of ${d._1} with same scope: ${d._2.map(a => s"line ${a.pos.line} column ${a.pos.column}").mkString(", ")}")
  }

  /**
    * Exception thrown in case of MiniScala type errors.
    */
  class TypeError(msg: String, node: AstNode) extends MiniScalaError(s"Type error: $msg", node.pos)
}
