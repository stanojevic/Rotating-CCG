package edin.ccg.transitions

trait TransitionController {

  def currentTransOptions(conf:Configuration) : List[TransitionOption]

  val allPossibleTransOptions : List[TransitionOption]

}

trait TransitionOption{

  def apply(conf: Configuration) : Configuration

}

