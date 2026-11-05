package miniscala

import miniscala.Ir.*

/**
  * Control-flow graphs of MiniScala programs, one per function, built from the intermediate
  * representation (see Ir.scala).
  */
object Cfg {

  /**
    * A node in a control-flow graph: one point in the execution of a function, at one of its
    * instructions. Two nodes are the same only if they are the same object, never because they
    * look alike.
    */
  sealed abstract class Node {

    def code: Code

    def index: Int

    /**
      * The instruction this node stands at.
      */
    def instr: Instr = code.instrs(index)
  }

  /**
    * An ordinary instruction.
    */
  class InstrNode(val code: Code, val index: Int) extends Node {
    override def toString: String = s"instruction $index"
  }

  /**
    * The point immediately before a call: the function and the arguments have been evaluated, and
    * control is about to be transferred to the callee.
    */
  class CallNode(val code: Code, val index: Int) extends Node {

    /**
      * The point immediately after this call.
      */
    val after: AfterCallNode = AfterCallNode(this)

    override def toString: String = s"call $index"
  }

  /**
    * The point immediately after a call, where the returned value is assigned to the result register.
    * Its only predecessor is the matching call node, and that edge is not ordinary control flow: it
    * stands for the execution of the callee.
    */
  class AfterCallNode(val call: CallNode) extends Node {

    def code: Code = call.code

    def index: Int = call.index

    override def toString: String = s"after call $index"
  }

  /**
    * The control-flow graph of one function. The nodes are listed in the order of the instructions.
    * The successors of a node are given by succ, and its predecessors by pred.
    */
  case class Graph(code: Code, nodes: List[Node],
                   succ: Map[Node, Set[Node]], pred: Map[Node, Set[Node]]) {

    /**
      * Where execution begins. It can have predecessors, since a loop jumps back to it.
      */
    def entry: Node = nodes.head

    /**
      * Where the function returns its result: the last instruction, which is always a return.
      */
    def exit: Node = nodes.last
  }

  /**
    * The control-flow graphs of a whole program. To go from a function to the one that encloses it,
    * use Fun.enclosing and look up the result in graphs.
    */
  case class Cfgs(main: Graph,
                  graphs: Map[Fun, Graph], // every function's graph
                  inner: Map[Node, Fun]) { // a Lambda or ClassDef node -> the function it creates

    /**
      * Navigates into the function created at the given node, if it is such a node.
      */
    def stepIn(n: Node): Option[Graph] = inner.get(n).map(fun => graphs(fun))
  }

  /**
    * Builds the control-flow graphs of a program.
    */
  def apply(program: Program): Cfgs = {
    def group(edges: List[(Node, Node)]): Map[Node, Set[Node]] =
      edges.groupBy((from, _) => from).map((from, es) => (from, es.map((_, to) => to).toSet)).withDefaultValue(Set())
    var graphs = Map[Fun, Graph]()
    var inner = Map[Node, Fun]()
    for (code <- program.codes) {
      val nodes = code.instrs.zipWithIndex.map({
        case (_: Call | _: NewObj, i) => val call = CallNode(code, i); (call, call.after)
        case (_, i) => val n = InstrNode(code, i); (n, n)
      })
      def enter(i: Target): Node = nodes(i)._1
      def leave(i: Target): Node = nodes(i)._2
      var edges = List[(Node, Node)]()
      for ((instr, i) <- code.instrs.zipWithIndex) {
        if (enter(i) ne leave(i))
          edges = (enter(i) -> leave(i)) :: edges
        val successors = instr match {
          case Branch(_, whenTrue, whenFalse, _) => List(enter(whenTrue), enter(whenFalse))
          case ArityBranch(_, _, whenMatch, whenNot, _) => List(enter(whenMatch), enter(whenNot))
          case Jump(target, _) => List(enter(target))
          case Return(_, _) | Abort(_) => Nil
          case _ => List(enter(i + 1))
        }
        edges = successors.map(s => leave(i) -> s) ++ edges
        instr match {
          case Lambda(_, fun, _, _) => inner = inner + (leave(i) -> fun)
          case ClassDef(fun, _, _) => inner = inner + (leave(i) -> fun)
          case _ =>
        }
      }
      // the nodes in instruction order, which the instruction vector already gives us
      val all = nodes.toList.flatMap((enter, leave) => if (enter eq leave) List(enter) else List(enter, leave))
      val succ = group(edges)
      val pred = group(edges.map((from, to) => (to, from)))
      graphs = graphs + (code.fun -> Graph(code, all, succ, pred))
    }
    Cfgs(graphs(program.main.fun), graphs, inner)
  }
}
