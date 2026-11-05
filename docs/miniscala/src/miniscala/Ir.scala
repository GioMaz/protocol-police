package miniscala

import miniscala.Ast.*

import java.util
import scala.collection.mutable

/**
  * Intermediate representation of MiniScala programs.
  *
  * Each function becomes a flat list of simple instructions. Each subexpression gets a register
  * that holds its value, each declaration gets a variable, and if, while and match become jumps.
  * See Cfg.scala for the control-flow graphs built from this representation.
  */
object Ir {

  /**
    * A register holds the value of one subexpression occurrence. Registers are numbered per function.
    */
  case class Reg(reg: Int) {
    override def toString: String = s"r$reg"
  }

  /**
    * A variable. One is made for each declaration: a parameter, a val, a var, or a pattern variable.
    * Two declarations of the same name are two different variables, so shadowing is already handled.
    *
    * @param name the name in the source program, used for printing
    * @param decl the declaration that introduced the variable
    * @param owner the function that declares the variable
    */
  class Var(val name: Id, val decl: AstNode, val owner: Fun) {

    /**
      * True for a var. A val holds a value, whereas a var holds the location of a cell that
      * assignments write to (see Alloc, Load and Store).
      */
    def mutable: Boolean = decl.isInstanceOf[VarDecl]

    override def toString: String = name
  }

  /**
    * A function: the program, a def, a lambda, or a class constructor. Every function except the
    * program has an enclosing function, so the functions form a tree with the program at the root.
    */
  class Fun(val name: String, val params: List[FunParam], val body: Exp, val enclosing: Option[Fun]) {
    override def toString: String = name
  }

  /**
    * A jump target: an index into a function's instruction vector.
    */
  type Target = Int

  /**
    * Instructions. The 'ast' field is the AST node this instruction came from.
    */
  sealed abstract class Instr {
    def ast: AstNode
  }

  case class Const(target: Reg, value: Literal, ast: AstNode) extends Instr
  case class Copy(target: Reg, src: Reg, ast: AstNode) extends Instr
  case class BinOp(target: Reg, op: Ast.BinOp, left: Reg, right: Reg, ast: AstNode) extends Instr
  case class UnOp(target: Reg, op: Ast.UnOp, arg: Reg, ast: AstNode) extends Instr
  case class Read(target: Reg, x: Var, ast: AstNode) extends Instr
  case class Bind(x: Var, src: Reg, ast: AstNode) extends Instr
  case class Alloc(target: Reg, ast: AstNode) extends Instr
  case class Load(target: Reg, cell: Reg, ast: AstNode) extends Instr
  case class Store(cell: Reg, src: Reg, ast: AstNode) extends Instr
  case class Lambda(target: Reg, fun: Fun, captured: List[Var], ast: AstNode) extends Instr
  case class ClassDef(fun: Fun, captured: List[Var], ast: AstNode) extends Instr
  case class Call(target: Reg, fun: Reg, args: List[Reg], ast: AstNode) extends Instr
  case class NewObj(target: Reg, klass: Id, args: List[Reg], ast: AstNode) extends Instr
  case class Lookup(target: Reg, obj: Reg, member: Id, ast: AstNode) extends Instr
  case class MakeTuple(target: Reg, elems: List[Reg], ast: AstNode) extends Instr
  case class Untuple(targets: List[Var], src: Reg, ast: AstNode) extends Instr
  case class Branch(cond: Reg, whenTrue: Target, whenFalse: Target, ast: AstNode) extends Instr
  case class ArityBranch(src: Reg, arity: Int, whenMatch: Target, whenNot: Target, ast: AstNode) extends Instr
  case class Jump(target: Target, ast: AstNode) extends Instr
  case class Return(src: Reg, ast: AstNode) extends Instr
  case class Abort(ast: AstNode) extends Instr

  /**
    * One function, translated: its parameters, and the instructions of its body.
    */
  case class Code(fun: Fun, params: List[Var], instrs: Vector[Instr])

  /**
    * A whole program, translated: one Code per function, with main first and the rest in the order
    * they are declared. Which function encloses which is given by Fun.enclosing.
    */
  case class Program(main: Code, codes: List[Code])

  /**
    * Translates a program to the intermediate representation.
    */
  def translate(program: Exp): Program = {
    val params = Vars.freeVars(program).toList.sorted.map(x => FunParam(x, None))
    val main = Fun("main", params, program, None)
    val pending = mutable.Queue[(Fun, Env)]((main, Map()))
    val codes = mutable.ArrayBuffer[Code]()
    while (pending.nonEmpty) {
      val (fun, env) = pending.dequeue()
      codes += Emitter(fun, env, pending).translate()
    }
    Program(codes.head, codes.toList)
  }

  /**
    * The registers and variables that the given instruction defines.
    */
  def defs(instr: Instr): Set[Reg | Var] = instr match {
    case Const(target, _, _) => set(target)
    case Copy(target, _, _) => set(target)
    case BinOp(target, _, _, _, _) => set(target)
    case UnOp(target, _, _, _) => set(target)
    case Read(target, _, _) => set(target)
    case Bind(x, _, _) => set(x)
    case Alloc(target, _) => set(target)
    case Load(target, _, _) => set(target)
    case Lambda(target, _, _, _) => set(target)
    case Call(target, _, _, _) => set(target)
    case NewObj(target, _, _, _) => set(target)
    case Lookup(target, _, _, _) => set(target)
    case MakeTuple(target, _, _) => set(target)
    case Untuple(targets, _, _) => targets.toSet
    case _: Store | _: ClassDef | _: Branch | _: ArityBranch | _: Jump | _: Return | _: Abort => Set()
  }

  /**
    * The registers and variables that the given instruction uses.
    */
  def uses(instr: Instr): Set[Reg | Var] = instr match {
    case Copy(_, src, _) => set(src)
    case BinOp(_, _, left, right, _) => set(left, right)
    case UnOp(_, _, arg, _) => set(arg)
    case Read(_, x, _) => set(x)
    case Bind(_, src, _) => set(src)
    case Load(_, cell, _) => set(cell)
    case Store(cell, src, _) => set(cell, src)
    case Lambda(_, _, captured, _) => captured.toSet
    case ClassDef(_, captured, _) => captured.toSet
    case Call(_, fun, args, _) => set(fun) ++ args
    case NewObj(_, _, args, _) => args.toSet
    case Lookup(_, obj, _, _) => set(obj)
    case MakeTuple(_, elems, _) => elems.toSet
    case Untuple(_, src, _) => set(src)
    case Branch(cond, _, _, _) => set(cond)
    case ArityBranch(src, _, _, _, _) => set(src)
    case Return(src, _) => set(src)
    case _: Const | _: Alloc | _: Jump | _: Abort => Set()
  }

  private def set(xs: (Reg | Var)*): Set[Reg | Var] = xs.toSet

  /**
    * Assigns display names to variables, disambiguating shadowing declarations.
    */
  class Names {

    private val names = util.IdentityHashMap[Var, String]()
    private val counts = mutable.Map[Id, Int]()

    def apply(x: Var): String =
      names.computeIfAbsent(x, _ => {
        val count = counts.getOrElse(x.name, 0) + 1
        counts.put(x.name, count)
        if (count == 1) x.name else s"${x.name}#$count"
      })
  }

  /**
    * Formats a program, showing the nesting of functions by indentation.
    */
  def format(program: Program): String = {
    val names = Names()
    val result = StringBuilder()
    def formatFun(code: Code, indent: String): Unit = {
      result ++= s"$indent${code.fun.name}(${code.params.map(names.apply).mkString(", ")}) {\n"
      for ((instr, i) <- code.instrs.zipWithIndex)
        result ++= s"$indent  ${"%3d".format(i)}: ${format(instr, names)}\n"
      result ++= s"$indent}\n"
      for (nested <- program.codes.filter(c => c.fun.enclosing.contains(code.fun)))
        formatFun(nested, indent + "  ")
    }
    formatFun(program.main, "")
    result.toString
  }

  /**
    * Formats a single instruction.
    */
  def format(instr: Instr, names: Names): String = instr match {
    case Const(target, value, _) => s"$target := ${Unparser.unparse(value)}"
    case Copy(target, src, _) => s"$target := $src"
    case BinOp(target, op, left, right, _) => s"$target := $left ${Unparser.unparse(op)} $right"
    case UnOp(target, op, arg, _) => s"$target := ${Unparser.unparse(op)}$arg"
    case Read(target, x, _) => s"$target := read ${names(x)}"
    case Bind(x, src, _) => s"bind ${names(x)} := $src"
    case Alloc(target, _) => s"$target := alloc"
    case Load(target, cell, _) => s"$target := load [$cell]"
    case Store(cell, src, _) => s"store [$cell] := $src"
    case Lambda(target, fun, _, _) => s"$target := lambda ${fun.name}"
    case ClassDef(fun, _, _) => s"class ${fun.name}"
    case Call(target, fun, args, _) => s"$target := call $fun(${args.mkString(", ")})"
    case NewObj(target, klass, args, _) => s"$target := new $klass(${args.mkString(", ")})"
    case Lookup(target, obj, member, _) => s"$target := $obj.$member"
    case MakeTuple(target, elems, _) => s"$target := (${elems.mkString(", ")})"
    case Untuple(targets, src, _) => s"untuple (${targets.map(names.apply).mkString(", ")}) := $src"
    case Branch(cond, whenTrue, whenFalse, _) => s"if $cond goto $whenTrue else goto $whenFalse"
    case ArityBranch(src, arity, whenMatch, whenNot, _) => s"if arity($src) == $arity goto $whenMatch else goto $whenNot"
    case Jump(target, _) => s"goto $target"
    case Return(src, _) => s"return $src"
    case Abort(_) => "abort"
  }

  /**
    * The environment maps identifiers to variables. A nested function is translated with the
    * environment of its enclosing function, so a captured identifier resolves to that function's
    * variable.
    */
  private type Env = Map[Id, Var]

  /**
    * Placeholder for a jump target that is patched once the target is known.
    */
  private val unresolved: Target = -1

  /**
    * Translates the body of one function.
    */
  private class Emitter(fun: Fun, outer: Env, pending: mutable.Queue[(Fun, Env)]) {

    private val instrs = mutable.ArrayBuffer[Instr]()
    private var nextReg = 0

    def translate(): Code = {
      val params = fun.params.map(p => declare(p.x, p))
      val env = outer ++ fun.params.map(p => p.x).zip(params)
      val result = translate(fun.body, env)
      emit(Return(result, fun.body))
      Code(fun, params, instrs.toVector)
    }

    private def translate(e: Exp, env: Env): Reg = e match {
      case value: Literal =>
        val target = reg()
        emit(Const(target, value, e))
        target
      case VarExp(x) =>
        val variable = lookup(x, env, e)
        val target = reg()
        emit(Read(target, variable, e))
        if (variable.mutable) { val value = reg(); emit(Load(value, target, e)); value } else target
      case BinOpExp(leftexp, op@(AndAndBinOp() | OrOrBinOp()), rightexp) =>
        val left = translate(leftexp, env)
        val target = reg()
        val branch = emit(Branch(left, unresolved, unresolved, e))
        val second = here
        emit(Copy(target, translate(rightexp, env), e))
        val jump = emit(Jump(unresolved, e))
        val shortcut = here
        emit(Copy(target, left, e))
        patch(jump, Jump(here, e))
        patch(branch, op match {
          case AndAndBinOp() => Branch(left, second, shortcut, e)
          case _ => Branch(left, shortcut, second, e)
        })
        target
      case BinOpExp(leftexp, op, rightexp) =>
        val left = translate(leftexp, env)
        val right = translate(rightexp, env)
        val target = reg()
        emit(BinOp(target, op, left, right, e))
        target
      case UnOpExp(op, exp) =>
        val arg = translate(exp, env)
        val target = reg()
        emit(UnOp(target, op, arg, e))
        target
      case IfThenElseExp(condexp, thenexp, elseexp) =>
        val cond = translate(condexp, env)
        val target = reg()
        val branch = emit(Branch(cond, unresolved, unresolved, e))
        val whenTrue = here
        emit(Copy(target, translate(thenexp, env), e))
        val jump = emit(Jump(unresolved, e))
        val whenFalse = here
        emit(Copy(target, translate(elseexp, env), e))
        patch(jump, Jump(here, e))
        patch(branch, Branch(cond, whenTrue, whenFalse, e))
        target
      case BlockExp(vals, vars, defs, classes, exps) =>
        var env2 = env
        for (d <- vals) {
          val value = translate(d.exp, env2)
          val variable = declare(d.x, d)
          emit(Bind(variable, value, d))
          env2 = env2 + (d.x -> variable)
        }
        for (d <- vars) {
          val cell = reg()
          emit(Alloc(cell, d))
          val value = translate(d.exp, env2)
          emit(Store(cell, value, d))
          val variable = declare(d.x, d)
          emit(Bind(variable, cell, d))
          env2 = env2 + (d.x -> variable)
        }
        val defvars = defs.map(d => declare(d.fun, d))
        env2 = env2 ++ defs.map(d => d.fun).zip(defvars)
        val closures = defs.map(d => {
          val target = reg()
          emit(Lambda(target, enqueue(d.fun, d.params, d.body, env2), captured(d, env2), d))
          target
        })
        for ((d, (variable, target)) <- defs.zip(defvars.zip(closures)))
          emit(Bind(variable, target, d))
        for (d <- classes)
          emit(ClassDef(enqueue(d.klass, d.params, d.body, env2), captured(d, env2), d))
        if (exps.isEmpty) unit(e) else exps.map(exp => translate(exp, env2)).last
      case TupleExp(exps) =>
        val elems = exps.map(exp => translate(exp, env))
        val target = reg()
        emit(MakeTuple(target, elems, e))
        target
      case MatchExp(exp, cases) =>
        val src = translate(exp, env)
        val target = reg()
        val tests = cases.map(c => (c, emit(ArityBranch(src, c.pattern.length, unresolved, here + 1, e))))
        emit(Abort(e))
        val jumps = for ((c, test) <- tests) yield {
          patch(test, ArityBranch(src, c.pattern.length, here, test + 1, e))
          val patvars = c.pattern.map(x => declare(x, c))
          emit(Untuple(patvars, src, c))
          emit(Copy(target, translate(c.exp, env ++ c.pattern.zip(patvars)), c))
          emit(Jump(unresolved, c))
        }
        jumps.foreach(jump => patch(jump, Jump(here, e)))
        target
      case CallExp(funexp, args) =>
        val fun = translate(funexp, env)
        val argregs = args.map(arg => translate(arg, env))
        val target = reg()
        emit(Call(target, fun, argregs, e))
        target
      case LambdaExp(params, body) =>
        val target = reg()
        emit(Lambda(target, enqueue(s"lambda@${e.pos}", params, body, env), captured(e, env), e))
        target
      case AssignmentExp(x, exp) =>
        val value = translate(exp, env)
        val variable = lookup(x, env, e)
        if (!variable.mutable)
          throw IrError(s"Illegal assignment, '$x' is not mutable", e)
        val cell = reg()
        emit(Read(cell, variable, e))
        emit(Store(cell, value, e))
        unit(e)
      case WhileExp(cond, body) =>
        val test = here
        val condreg = translate(cond, env)
        val branch = emit(Branch(condreg, unresolved, unresolved, e))
        val whenTrue = here
        translate(body, env)
        emit(Jump(test, e))
        patch(branch, Branch(condreg, whenTrue, here, e))
        unit(e)
      case DoWhileExp(body, cond) =>
        val whenTrue = here
        translate(body, env)
        val condreg = translate(cond, env)
        val branch = emit(Branch(condreg, whenTrue, unresolved, e))
        patch(branch, Branch(condreg, whenTrue, here, e))
        unit(e)
      case NewObjExp(klass, args) =>
        val argregs = args.map(arg => translate(arg, env))
        val target = reg()
        emit(NewObj(target, klass, argregs, e))
        target
      case LookupExp(objexp, member) =>
        val obj = translate(objexp, env)
        val target = reg()
        emit(Lookup(target, obj, member, e))
        target
    }

    private def reg(): Reg = {
      nextReg += 1
      Reg(nextReg - 1)
    }

    private def emit(instr: Instr): Target = {
      instrs += instr
      instrs.length - 1
    }

    private def patch(at: Target, instr: Instr): Unit = instrs(at) = instr

    private def here: Target = instrs.length

    private def unit(e: Exp): Reg = {
      val target = reg()
      emit(MakeTuple(target, Nil, e))
      target
    }

    private def declare(x: Id, decl: AstNode): Var = Var(x, decl, fun)

    private def lookup(x: Id, env: Env, n: AstNode): Var =
      env.getOrElse(x, throw IrError(s"Unknown identifier '$x'", n))

    /**
      * Makes a function for a nested declaration or lambda, to be translated later.
      */
    private def enqueue(name: String, params: List[FunParam], body: Exp, env: Env): Fun = {
      val nested = Fun(name, params, body, Some(fun))
      pending += ((nested, env))
      nested
    }

    /**
      * The variables that a nested function uses from the functions around it. These are its free
      * identifiers, the same ones Compiler passes to a closure. Vars.freeVars also looks inside
      * deeper functions, so a variable used by a lambda within a lambda is included as well.
      */
    private def captured(n: DeclOrExp, env: Env): List[Var] = {
      val free = n match {
        case d: Decl => Vars.freeVars(d)
        case exp: Exp => Vars.freeVars(exp)
      }
      free.toList.sorted.flatMap(x => env.get(x))
    }
  }

  /**
    * Exception thrown in case of errors found while building the intermediate representation.
    */
  class IrError(msg: String, node: AstNode) extends MiniScalaError(msg, node.pos)
}
