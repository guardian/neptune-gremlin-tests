case class CommandlineArgs(neptuneHost:Option[String], showHelp:Boolean)

object CommandlineArgs {
  def apply(args: Array[String]): CommandlineArgs = {

    def nextOption(opts: CommandlineArgs, list: List[String]): CommandlineArgs = list match {
      case Nil => opts
      case "--neptune-host" :: value :: tail =>
        nextOption(opts.copy(neptuneHost = Some(value)), tail)
      case "--help" :: tail =>
        nextOption(opts.copy(showHelp = true), tail)
      case _ :: tail =>
        nextOption(opts, tail)
    }

    val opts = nextOption(new CommandlineArgs(None, false), args.toList)
    if(opts.showHelp) {
      showHelp()
      sys.exit(1)
    }
    opts
  }

  def showHelp(): Unit = {
    println("Test importer for recipes data in Neptune")
    println("Usage: java -jar neptune-gremlin-tests.jar [--neptune-host hostname] [--help]")
  }
}

