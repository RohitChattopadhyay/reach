package org.clulab.reach.focusedreading.reinforcement_learning.environment

import org.clulab.reach.focusedreading.reinforcement_learning.actions.Action
import org.clulab.reach.focusedreading.reinforcement_learning.states.State

/**
  * Created by enrique on 31/03/17.
  */
trait Environment {
  def possibleActions(): Set[Action]
  def executePolicy(action:Action, persist:Boolean = true):Double
  def observeState:State
  def finishedEpisode:Boolean
}
