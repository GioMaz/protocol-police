package miniscala

import miniscala.Cfg.*
import miniscala.Ir.*

import java.io.{File, PrintWriter}
import java.util

/**
  * Output of control-flow graphs in Graphviz dot format.
  */
object Cfg2Dot {

  def convert(cfgs: Cfgs, filename: String): Unit = {
    val pw = PrintWriter(File(filename))
    val names = Names()
    val ids = util.IdentityHashMap[Node, String]()
    for (g <- cfgs.graphs.values; n <- g.nodes)
      ids.put(n, s"n${ids.size() + 1}")
    pw.println("digraph CFG {")
    pw.println(" node [shape=box,style=rounded,fontname=\"monospace\"];")
    def cluster(g: Graph, indent: String): Unit = {
      pw.println(s"""$indent subgraph "cluster_${ids.get(g.entry)}" {""")
      pw.println(s"""$indent  label="${label(signature(g.code, names))}"; style=dashed;""")
      for (n <- g.nodes)
        pw.println(s"""$indent  ${ids.get(n)} [label="${label(text(n, names))}"${source(n)}];""")
      for (nested <- cfgs.graphs.values.filter(h => h.code.fun.enclosing.exists(f => f eq g.code.fun)))
        cluster(nested, indent + " ")
      pw.println(s"$indent }")
    }
    cluster(cfgs.main, "")
    for (g <- cfgs.graphs.values; n <- g.nodes) {
      for (s <- g.succ(n))
        // the edge from a call to its after-call node stands for the execution of the callee
        pw.println(s" ${ids.get(n)} -> ${ids.get(s)}${if (s.isInstanceOf[AfterCallNode]) " [style=dotted]" else ""};")
      for (inner <- cfgs.stepIn(n))
        pw.println(s" ${ids.get(n)} -> ${ids.get(inner.entry)} [style=dashed,color=gray];")
    }
    pw.println("}")
    pw.close()
  }

  private def signature(code: Code, names: Names): String =
    s"${code.fun.name}(${code.params.map(x => names(x)).mkString(", ")})"

  private def text(n: Node, names: Names): String = (n, n.instr) match {
    // the call transfers control, and the result is assigned at the after-call node
    case (_: CallNode, Call(_, fun, args, _)) => s"${n.index}: call $fun(${args.mkString(", ")})"
    case (_: CallNode, NewObj(_, klass, args, _)) => s"${n.index}: new $klass(${args.mkString(", ")})"
    case (_: AfterCallNode, Call(target, _, _, _)) => s"${n.index}: $target := result"
    case (_: AfterCallNode, NewObj(target, _, _, _)) => s"${n.index}: $target := result"
    case _ => s"${n.index}: ${format(n.instr, names)}"
  }

  /**
    * The source code an instruction came from, shown when the mouse is over the node.
    */
  private def source(n: Node): String = {
    val source = Unparser.unparse(n.instr.ast)
    val shown = if (source.length > 60) source.take(60) + "..." else source
    s""",tooltip="${label(shown)} (line ${n.instr.ast.pos.line})""""
  }


  private def label(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
