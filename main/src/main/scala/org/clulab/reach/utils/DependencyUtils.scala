package org.clulab.reach.utils

import org.clulab.processors.Sentence
import org.clulab.struct.{DirectedGraph, Interval}

/**
 * Utility functions for use with directed (dependency) graphs
 * User: danebell
 * Date: 2/23/15
 */
object DependencyUtils {

  val defaultPolicy: (Seq[Int]) => Int = _.last

  /**
   * Given an Interval, finds the minimal span covering all of the Interval's nodes' children (recursively).
   *
   * @param span Interval of nodes
   * @param sent the sentence over which the interval applies
   * @return the minimal Interval that contains all the nodes that are children of span's nodes
   */
  def subgraph(span: Interval, sent: Sentence): Option[Interval] = {
    val graph = sent.dependencies.getOrElse(return None)

    val heads = if (span.size < 2) Seq(span.start) else findHeadsStrict(span, sent)

    @annotation.tailrec
    def followTrail(remaining: Seq[Int], results: Seq[Int]): Seq[Int] = remaining match {
      case Nil => results
      case first +: rest if results contains first => followTrail(rest, results)
      case first +: rest =>
        val children: Seq[Int] = try {
          graph.getOutgoingEdges(first).map(_._1)
        } catch {
          case e: Exception =>
            Nil
        }
        followTrail(children ++ rest, first +: results)
    }

    val outgoing = (for (h <- heads) yield followTrail(Seq(h), Nil)).flatten.distinct

    // outgoing may only have a single index
    if (outgoing.isEmpty) Some(span) // no head found, so no expansion is possible
    else if (outgoing.length == 1) Some(Interval(outgoing.head, outgoing.head + 1))
    else Some(Interval(outgoing.min, outgoing.max+1))
  }

  /**
   * Finds the highest node (i.e. closest to a root) in an Interval of a directed graph. If there are multiple nodes of
   * the same rank, chooseWhich adjudicates which single node is returned.
   *
   * @param span an Interval of nodes
   * @param graph a directed graph containing the nodes in span
   * @param chooseWhich a function deciding which of multiple heads is returned; the rightmost head selected by default
   * @return the single node which is closest to the root among those in span
   */
  def findHead(span: Interval, graph: DirectedGraph[String], chooseWhich:(Seq[Int]) => Int = defaultPolicy): Int = {
    chooseWhich(findHeads(span, graph))
  }

  /**
   * Finds the highest node (i.e. closest to a root) in an Interval of a directed graph. If there are multiple nodes of
   * the same rank, all are returned.
   *
   * @param span an Interval of nodes
   * @param graph a directed graph containing the nodes in span
   * @return the single node which is closest to the root among those in span
   */
  def findHeads(span: Interval, graph: DirectedGraph[String]): Seq[Int] = {

    def getIncoming(i: Int) = graph.incomingEdges.lift(i).getOrElse(Array.empty).map(_._1)

    def loopfullFollowTrail(i: Int, heads:Seq[Int]): Int = {
      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty => i
        // Loop invariant: i is never already in heads, and this line prevents that
        case loop if loop.head == i || heads.contains(loop.head) => i
        case found if found.min < span.start || span.end <= found.max => loopfullFollowTrailOut(found.head, i +: heads, i)
        case _ => loopfullFollowTrail(incoming.head, i +: heads)
      }
    }

    def loopfullFollowTrailOut(i: Int, heads: Seq[Int], highest: Int): Int = {
      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty => highest
        // Loop invariant: i is never already in heads, and this line prevents that
        case loop if loop.head == i || heads.contains(loop.head) => highest
        case outgoing if outgoing.min < span.start || span.end <= outgoing.max => loopfullFollowTrailOut(incoming.head, i +: heads, highest)
        case _ => loopfullFollowTrail(incoming.head, i +: heads)
      }
    }

    def looplessFollowTrail (i: Int, heads:Seq[Int]): Seq[Int] = {

      // dependents
      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty | valid.contains(i) => Seq(i)
        case found if found.min < span.start | found.max > (span.end - 1) => looplessFollowTrailOut(found.head, heads ++ Seq(i), i)
        case _ => looplessFollowTrail(incoming.head, heads ++ Seq(i))
      }
    }

    def looplessFollowTrailOut (i: Int, heads:Seq[Int], highest: Int): Seq[Int] = {

      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty | valid.contains(i) => Seq(highest)
        case outgoing if outgoing.min < span.start | outgoing.max > (span.end - 1) => looplessFollowTrailOut (incoming.head, heads ++ Seq(i), highest)
        case _ => looplessFollowTrail (incoming.head, heads ++ Seq(i))
      }
    }
    
    def loopfullHeads = for (i <- span.start until span.end) yield loopfullFollowTrail(i, Nil)

    def looplessHeads = for (i <- span.start until span.end) yield looplessFollowTrail(i, Nil)

    def loopfullFiltered = loopfullHeads.distinct.sorted
    
    def looplessFiltered = looplessHeads.flatten.distinct.sorted
    
    def doubleFiltered = {
      val loopfullResult = loopfullFiltered
      val looplessResult = looplessFiltered
      
      require(loopfullResult == looplessResult)
      loopfullResult
    }
    
    // Use looplessFiltered for old and potentially faster functionality which will break on loops
    def singleFiltered = loopfullFiltered
    //def singleFiltered = looplessFiltered
    
    // Use singleFiltered to not perform the test
    //doubleFiltered 
    singleFiltered
  }

  /**
   * Find the single highest node in an interval of a dependency graph, ignoring punctuation, coordinations, and
   * prepositions.
   * @param span the interval within which to search
   * @param sent the Sentence within which to look
   * @param chooseWhich the function to adjudicate which is highest when there's a tie
   * @return Option containing the highest node index, or None if no such node is found
   */
  def findHeadStrict(span: Interval, sent: Sentence, chooseWhich:(Seq[Int]) => Int = defaultPolicy): Option[Int] = {
    val hds = findHeadsStrict(span, sent)
    hds match{
      case valid if valid.nonEmpty => Some(chooseWhich(valid))
      case _ => None
    }
  }

  /**
   * Find the highest nodes in an interval of a dependency graph, ignoring punctuation, coordinations, and prepositions.
   * Allows multiple node indices to be "highest" in the case of a tie.
   * @param span the interval within which to search
   * @param sent the Sentence within which to look
   * @return Option containing a sequence of highest node indices, or None if no such node is found
   */
  def findHeadsStrict(span: Interval, sent: Sentence): Seq[Int] = {

    if (sent.dependencies.isEmpty) return Nil

    val stopTags = "(.|,|\\(|\\)|:|''|``|#|$|CC|TO|IN)"

    def getIncoming(i: Int) = sent.dependencies.get.incomingEdges.lift(i).getOrElse(Array.empty).map(_._1)
 
    def loopfullFollowTrail (i: Int, heads: Seq[Int]): Int = {
      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty => i
        // Loop invariant: i is never already in heads, and this line prevents that
        case loop if loop.head == i || heads.contains(loop.head) => i
        case found if found.min < span.start || span.end <= found.max => loopfullFollowTrailOut(found.head, i +: heads, i)
        case _ => loopfullFollowTrail(incoming.head, i +: heads)
      }
    }

    def loopfullFollowTrailOut (i: Int, heads: Seq[Int], highest: Int): Int = {
      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty => highest
        // Loop invariant: i is never already in heads, and this line prevents that
        case loop if loop.head == i || heads.contains(loop.head) => highest
        case outgoing if outgoing.min < span.start || span.end <= outgoing.max => loopfullFollowTrailOut(incoming.head, i +: heads, highest)
        case _ => loopfullFollowTrail(incoming.head, i +: heads)
      }
    }
    
    def looplessFollowTrail (i: Int, heads:Seq[Int]): Seq[Int] = {

      //// dependents
      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty | valid.contains(i) =>  Seq(i)
        case found if found.min < span.start | found.max > (span.end - 1) => looplessFollowTrailOut(found.head, heads ++ Seq(i), i)
        case _ => looplessFollowTrail(incoming.head, heads ++ Seq(i))
      }
    }

    def looplessFollowTrailOut (i: Int, heads:Seq[Int], highest: Int): Seq[Int] = {

      val incoming = getIncoming(i)

      incoming match {
        case valid if valid.isEmpty | valid.contains(i) => Seq(highest)
        case outgoing if outgoing.min < span.start | outgoing.max > (span.end - 1) => looplessFollowTrailOut (incoming.head, heads ++ Seq(i), highest)
        case _ => looplessFollowTrail (incoming.head, heads ++ Seq(i))
      }
    }
    
    def loopfullHeads = for (i <- span.start until span.end) yield loopfullFollowTrail(i, Nil)
    
    def looplessHeads = for (i <- span.start until span.end) yield looplessFollowTrail(i, Nil)

    def loopfullFiltered = loopfullHeads.distinct.filter(x => !(sent.tags.get(x) matches stopTags)).sorted
    
    def looplessFiltered = looplessHeads.flatten.distinct.filter(x => !(sent.tags.get(x) matches stopTags)).sorted
    
    def doubleFiltered = {
      val loopfullResult = loopfullFiltered
      val looplessResult = looplessFiltered
      
      require(loopfullResult == looplessResult)
      loopfullResult
    }
    
    // Use looplessFiltered for old and potentially faster functionality which will break on loops
    def singleFiltered = loopfullFiltered
    //def singleFiltered = looplessFiltered
    
    // Use singleFiltered to not perform the test
    //doubleFiltered 
    singleFiltered
  }


  /**
   * Finds the highest node (i.e. closest to a root) in an Interval of a directed graph, ignoring the graph outside the
   * Interval. If there are multiple nodes of the same rank, chooseWhich adjudicates which single node is returned.
   * <p>
   * Crucially, any node that has an incoming edge from outside the Interval is considered a head. This is efficient if
   * you know your span has a single head that is also within the span.
   *
   * @param span an Interval of nodes
   * @param graph a directed graph containing the nodes in span
   * @param chooseWhich a function deciding which of multiple heads is returned; the rightmost head selected by default
   * @return the single node which is closest to the root among those in span
   */
  def findHeadLocal(span: Interval, graph: DirectedGraph[String], chooseWhich:(Seq[Int]) => Int = defaultPolicy): Int = {
    chooseWhich(findHeadsLocal(span, graph))
  }

  /**
   * Finds the highest node (i.e. closest to a root) in an Interval of a directed graph. If there are multiple nodes of
   * the same rank, all are returned.
   * <p>
   * Crucially, any node that has an incoming edge from outside the Interval is considered a head. This is efficient if
   * you know your span has a single head that is also within the span.
   *
   * @param span an Interval of nodes
   * @param graph a directed graph containing the nodes in span
   * @return the single node which is closest to the root among those in span
   */
  def findHeadsLocal(span: Interval, graph: DirectedGraph[String]): Seq[Int] = {

    def followTrail (i: Int, heads:Seq[Int]): Seq[Int] = {

      // dependents
      val incoming = graph.getIncomingEdges(i).map(_._1)

      incoming match {
        case valid if valid.isEmpty | valid.contains(i) =>  Seq(i)
        // This does take into account a loop in heads.contains(i)
        case found if heads.contains(i) | found.min < span.start | found.max > (span.end - 1) => Seq(i)
        case _ => followTrail(incoming.head, heads ++ Seq(i))
      }
    }

    val heads = for (i <- span.start until span.end) yield followTrail(i, Nil)
    
    heads.flatten.distinct.sorted    
  }

  /**
   *
   * @param a Interval in Sentence sentA
   * @param b Interval in Sentence sentB
   * @param sentA Sentence containing a
   * @param sentB Sentence containing b
   * @return returns true if Interval a contains Interval b or vice versa
   */
  def nested(a: Interval, b: Interval, sentA: Sentence, sentB: Sentence): Boolean = {
    if (sentA != sentB) return false

    val graph = sentA.dependencies.getOrElse(return false)

    val aSubgraph = subgraph(a, sentA)
    val bSubgraph = subgraph(b, sentB)

    if((aSubgraph.isDefined && aSubgraph.get.contains(b)) ||
      (bSubgraph.isDefined && bSubgraph.get.contains(a))) true
    else false
  }
}
