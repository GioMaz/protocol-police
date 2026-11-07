package miniscala

import miniscala.Ast.*

/**
  * Unparser for MiniScala.
  */
object Unparser {

  /**
    * Unparse function.
    * Used for all kinds of AstNode objects, including Exp objects (see Ast.scala).
    */
  def unparse(n: AstNode): String = n match {
    case IntLit(c) => s"$c"
    case FloatLit(c) => s"${c}f"
    case BoolLit(c) => s"$c"
    case StringLit(c) => s"\"$c\""
    case NullLit() => "null"
    case VarExp(x) => s"$x"
    case BinOpExp(leftexp, op, rightexp) => s"(${unparse(leftexp)} ${unparse(op)} ${unparse(rightexp)})"
    case UnOpExp(op, exp) => s"${unparse(op)}${unparse(exp)}"
    case IfThenElseExp(condexp, thenexp, elseexp) => s"if (${unparse(condexp)}) ${unparse(thenexp)} else ${unparse(elseexp)}"
    case BlockExp(vals, vars, defs, classes, exps) => s"{ ${(vals ++ vars ++ defs ++ classes ++ exps).map(unparse).mkString("; ")} }"
    case WhileExp(cond, body) => s"while (${unparse(cond)}) ${unparse(body)}"
    case DoWhileExp(body, cond) => s"do ${unparse(body)} while (${unparse(cond)})"
    case AssignmentExp(x, exp) => s"$x = ${unparse(exp)}"
    case TupleExp(exps) => s"(${exps.map(unparse).mkString(",")})"
    case MatchExp(exp, cases) => s"${unparse(exp)} match { ${cases.map(unparse).mkString(" ")} }"
    case CallExp(funexp, args) => s"${unparse(funexp)}(${args.map(unparse).mkString(",")})"
    case LambdaExp(params, body) => s"((${params.map(unparse).mkString(",")}) => ${unparse(body)})"
    case NewObjExp(klass, args) => s"new $klass(${args.map(unparse).mkString(",")})"
    case LookupExp(objexp, member) => s"${unparse(objexp)}.$member"
    case ValDecl(x, opttype, exp) => s"val $x${unparse(opttype)} = ${unparse(exp)}"
    case VarDecl(x, opttype, exp) => s"var $x${unparse(opttype)} = ${unparse(exp)}"
    case DefDecl(fun, params, optrestype, body) => s"def $fun(${params.map(unparse).mkString(",")})${unparse(optrestype)} = ${unparse(body)}"
    case ClassDecl(klass, params, body) => s"class $klass(${params.map(unparse).mkString(",")}) ${unparse(body)}"
    case FunParam(x, opttype) => s"$x${unparse(opttype)}"
    case MatchCase(pattern, exp) => s"case (${pattern.mkString(",")}) => ${unparse(exp)}"
    case PlusBinOp() => "+"
    case MinusBinOp() => "-"
    case MultBinOp() => "*"
    case DivBinOp() => "/"
    case EqualBinOp() => "=="
    case LessThanBinOp() => "<"
    case LessThanOrEqualBinOp() => "<="
    case ModuloBinOp() => "%"
    case MaxBinOp() => "max"
    case AndBinOp() => "&"
    case OrBinOp() => "|"
    case AndAndBinOp() => "&&"
    case OrOrBinOp() => "||"
    case NegUnOp() => "-"
    case NotUnOp() => "!"
    case IntType() => "Int"
    case FloatType() => "Float"
    case BoolType() => "Boolean"
    case StringType() => "String"
    case TupleType(Nil) => "Unit"
    case TupleType(types) => types.map(unparse).mkString("(", ", ", ")")
    case FunType(paramtypes, restype) => s"${paramtypes.map(unparse).mkString("(", ", ", ")")} => ${unparse(restype)}"
    case MutableType(thetype) => s"Mutable[${unparse(thetype)}]" // (unreachable case)
    case ClassNameType(klass) => klass
    case DynamicClassType(srcpos) => s"class[line ${srcpos.line} column ${srcpos.column}]" // used by Interpreter only
    case StaticClassType(srcpos, _, _, _, _) => s"class[line ${srcpos.line} column ${srcpos.column}]" // used by TypeChecker only
    case NullType() => "Null"
  }

  /**
    * Unparse function for optional type annotations.
    */
  def unparse(ot: Option[Type]): String = ot match {
    case Some(t) => ": " + unparse(t)
    case None => ""
  }
}
