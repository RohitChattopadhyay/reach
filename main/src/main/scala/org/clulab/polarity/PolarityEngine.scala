package org.clulab.polarity

import com.typesafe.scalalogging.LazyLogging
import org.clulab.reach.mentions.BioEventMention

/**
  * Interface to multiple polarity engine implementations
  */
trait PolarityEngine extends LazyLogging{

  /**
    * Abstract implementation of the polarity classifier. Meant to be overridden with the specific implementation
    * @param evt BioEventMention to be operated on
    * @return Outcome of polarity classification
    */
  def computePolarity(evt:BioEventMention):Polarity

  /**
    * Computes the polarity and returns a modified instance of the event mention of it with the polarity value returned
    * by computePolarity.
    *
    * @param evt BioEventMention to be operated on
    * @return Modified version of evt
    */
  def assignPolarity(evt: BioEventMention):BioEventMention = {
    val basePolarity = getPolarityFromLabel(evt)

    if(basePolarity == NeutralPolarity)
      evt
    else {
      val predictedPolarity = computePolarity(evt)

      predictedPolarity match {
        case NeutralPolarity =>
          // Technically we should never fall here, although the design doesn't rule it out, hence log this branching
          logger.debug("Predicted neutral polarity for a polarized event")
          evt
        case PositivePolarity if basePolarity == NegativePolarity =>
          val newLabel = "Positive_" + evt.label.substring(9)
          copyEventMentionWithNewLabel(evt, newLabel)
        case NegativePolarity if basePolarity == PositivePolarity =>
          val newLabel = "Negative_" + evt.label.substring(9)
          copyEventMentionWithNewLabel(evt, newLabel)
        case _ =>
          // This case means no change in polarity needed, just return the event unchanged
          evt
      }

    }
  }

  /**
    * Extracts the base polarity of an event assigned by the grammar
    * @param evt BioEventMention to be operated on
    * @return Outcome of polarity classification
    */
  protected def getPolarityFromLabel(evt: BioEventMention):Polarity = {
    val label = evt.label

    if (label startsWith "Positive_")
      PositivePolarity
    else if (label startsWith "Negative_")
      NegativePolarity
    else
      NeutralPolarity
  }

  /**
    * Create a new event as a copy of the original with the newly provided label.
    * @param evt Source event
    * @param newLabel Label to be carried by the copy
    * @return Resulting copy with the new label
    */
  private def copyEventMentionWithNewLabel(evt:BioEventMention, newLabel:String):BioEventMention = {
    val newLabels = newLabel +: evt.labels.tail
    // trigger labels should match event labels
    val newTrigger = evt.trigger.copy(labels = newLabels)
    // return new mention with flipped label
    new BioEventMention(evt.copy(labels = newLabels, trigger = newTrigger), evt.isDirect)
  }
}
