package com.fsist.safepickle

/** A way to analyze a reified tree of nodes, with a base concrete type of `Node`,
  * representing pickled primitive values.
  */
trait TreeParser[Node] {
  def nodeType(node: Node): TreeNodeType

  def int(node: Node): Int
  def long(node: Node): Long
  def float(node: Node): Float
  def double(node: Node): Double
  def string(node: Node): String
  def boolean(node: Node): Boolean
  def array(node: Node): Iterator[Node]
  def obj(node: Node): Iterator[(String, Node)]
}

/** A Reader implementation for a reified tree of nodes representing pickled primitive values. */
class TreePickleReader[Node](parser: TreeParser[Node], root: Node) extends PickleReader {

  import TreePickleReader._

  private var node = root
  private val stack = collection.mutable.Stack[StackedState[Node]]()
  private var ended = false

  // Initialized correctly by the call to processNewNode()
  private var currTokenType: TokenType = TokenType.Null

  private var nodeAttributeName: Option[String] = None

  processNewNode()
  
  def currentNode: Node = node

  private def enterArray(node: Node): Unit = {
    stack.push(new ArrayState(parser.array(node)))
  }

  private def enterObject(node: Node): Unit = {
    // An object, or associative array, usually doesn't guarantee it will preserve field order.
    // However, for Autogen unpickling, we need to guarantee that the special fields $type, etc. will always come first.

    var tpe : Option[(String, Node)] = None
    val dollars = Vector.newBuilder[(String, Node)]
    val others = Vector.newBuilder[(String, Node)]

    for (tuple @ (key, value) <- parser.obj(node)) {
      if (key == "$type") tpe = Some(tuple)
      else if (key.startsWith("$")) dollars += tuple
      else others += tuple
    }

    val ordered = tpe.toVector ++ dollars.result() ++ others.result()
    
    stack.push(new ObjectState(ordered.iterator))
  }

  /** Called whenever we encounter a new node, which should at this point be stored in the `node` field.
    *
    * Updates the currTokenType field and, if the new node is an array or object, enters it.
    */
  private def processNewNode(): Unit = {
    currTokenType = parser.nodeType(node) match {
      case TreeNodeType.Array =>
        enterArray(node)
        TokenType.ArrayStart
      case TreeNodeType.Object =>
        enterObject(node)
        TokenType.ObjectStart
      case TreeNodeType.Boolean => TokenType.Boolean
      case TreeNodeType.Int => TokenType.Int
      case TreeNodeType.Long => TokenType.Long
      case TreeNodeType.Float => TokenType.Float
      case TreeNodeType.Double => TokenType.Double
      case TreeNodeType.Null => TokenType.Null
      case TreeNodeType.String => TokenType.String
      case TreeNodeType.Other => TokenType.Other
    }
  }

  override def next(): Boolean = {

    /** Advances to the next member of the array, updating `node` and `currTokenType`.
      * If the next encountered node is an array or object, enters it.
      */
    def nextInArray(array: ArrayState[Node]): Unit = {
      if (array.iter.hasNext) {
        node = array.iter.next()
        processNewNode()
      }
      else {
        currTokenType = TokenType.ArrayEnd
      }
    }

    /** Advances to the next member of the object, updating `nodeAttributeName`, `node` and `currTokenType`.
      * Doesn't call `processNewNode`, because our next token is the node's attribute name, not the node itself.
      */
    def nextInObject(obj: ObjectState[Node]): Unit = {
      if (obj.iter.hasNext) {
        val (name, value) = obj.iter.next()
        nodeAttributeName = Some(name)
        currTokenType = TokenType.AttributeName
        node = value
      }
      else {
        currTokenType = TokenType.ObjectEnd
      }
    }

    def leave(): Unit = {
      stack.pop()
      if (stack.isEmpty) {
        ended = true
      }
      else stack.top match {
        case array@ArrayState(iter) => nextInArray(array)
        case obj@ObjectState(iter) => nextInObject(obj)
      }
    }

    if (ended) false
    else if (currTokenType == TokenType.ArrayStart) {
      val array@ArrayState(iter) = stack.top
      nextInArray(array)

      true
    }
    else if (currTokenType == TokenType.ArrayEnd) {
      leave()
      !ended
    }
    else if (currTokenType == TokenType.ObjectStart) {
      val obj@ObjectState(iter) = stack.top
      nextInObject(obj)

      true
    }
    else if (currTokenType == TokenType.ObjectEnd) {
      leave()
      !ended
    }
    else if (stack.isEmpty) {
      ended = true
      false
    }
    else if (currTokenType == TokenType.AttributeName) {
      nodeAttributeName = None
      processNewNode()
      true
    }
    else stack.top match {
      case array@ArrayState(iter) =>
        nextInArray(array)
        true
      case obj@ObjectState(iter) =>
        nextInObject(obj)
        true
    }
  }

  def atEof: Boolean = ended

  override def tokenType: TokenType = currTokenType

  def int: Int = parser.int(node)
  def long: Long = parser.long(node)
  def float: Float = parser.float(node)
  def double: Double = parser.double(node)
  def string: String = parser.string(node)
  def boolean: Boolean = parser.boolean(node)
  def attributeName: String = nodeAttributeName.get
}

object TreePickleReader {

  private trait StackedState[Node]

  private case class ObjectState[Node](iter: Iterator[(String, Node)]) extends StackedState[Node]

  private case class ArrayState[Node](iter: Iterator[Node]) extends StackedState[Node]

}
