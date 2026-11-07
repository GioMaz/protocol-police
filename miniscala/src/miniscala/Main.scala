package miniscala

import miniscala.parser.Parser

object Main {

  /**
    * The main entry of MiniScala.
    */
  def main(args: Array[String]): Unit = {
    try {
      // read the command-line arguments
      Options.read(args)
      val file = Options.file.get

        // parse the program
        val program = Parser.parse(Parser.readFile(file))

        // unparse the program, if enabled
        if (Options.unparse)
          println(Unparser.unparse(program))

        // type check the program, if enabled
        if (Options.types) {
          val initialTypeEnv = TypeChecker.makeInitialTypeEnv(program)
          TypeChecker.typeCheck(program, initialTypeEnv, Map())
        }

        // execute the program, if enabled
        if (Options.run) {
          val initialEnv = Interpreter.makeInitialEnv(program)
          time {
            val (result, _) = Interpreter.eval(program, initialEnv, Map(), Map())
            println(s"Output: ${Interpreter.valueToString(result)}")
          }
        }

        // translate to the intermediate representation and build control-flow graphs, if enabled
        if (Options.ir || Options.cfg) {
          val ir = Ir.translate(program)
          if (Options.ir)
            print(Ir.format(ir))
          if (Options.cfg) {
            val outfile = (if (file.endsWith(".s")) file.substring(0, file.length - 2) else file) + ".cfg.dot"
            Cfg2Dot.convert(Cfg(ir), outfile)
            println(s"Control-flow graphs written to $outfile")
          }
        }

    } catch { // report all errors to the console
      case e: Options.OptionsError =>
        println(e.getMessage)
        println(Options.usage)
      case e: MiniScalaError =>
        println(e.getMessage)
    }
  }

  def time(block: => Unit): Unit = {
    val t0 = System.nanoTime()
    block
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0)/1000000 + "ms")
  }
}
