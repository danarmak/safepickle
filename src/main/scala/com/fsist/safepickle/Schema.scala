package com.fsist.safepickle

import scala.reflect.runtime.universe._

/** A schema describes the values that the associated pickler can write and read. */
sealed trait Schema {
  /** The Scala type corresponding to this schema; normally this is the associated `pickler.ttag.tpe`. */
  def tpe: Type

  /** Copy this Schema, replacing the tpe value. */
  def withTpe(tpe: Type): Schema
}

object Schema {
  implicit def fromPickler[T](implicit pickler: Pickler[T]): Schema = pickler.schema

  sealed trait AtomicConst[T] {
    def constant: T
    def label: Option[String]
  }

  case class SShort(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SShortConst(tpe: Type, constant: Short, label: Option[String] = None) extends Schema with AtomicConst[Short] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SInt(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SIntConst(tpe: Type, constant: Int, label: Option[String] = None) extends Schema with AtomicConst[Int] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SLong(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SLongConst(tpe: Type, constant: Long, label: Option[String] = None) extends Schema with AtomicConst[Long] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SFloat(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SFloatConst(tpe: Type, constant: Float, label: Option[String] = None) extends Schema with AtomicConst[Float] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SDouble(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SDoubleConst(tpe: Type, constant: Double, label: Option[String] = None) extends Schema with AtomicConst[Double] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SBoolean(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SBooleanConst(tpe: Type, constant: Boolean, label: Option[String] = None) extends Schema with AtomicConst[Boolean] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SNull(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SString(tpe: Type) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  case class SStringConst(tpe: Type, constant: String, label: Option[String] = None) extends Schema with AtomicConst[String] {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  /** An array whose items have the same schema. */
  case class SArray(tpe: Type, member: Schema) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  /** An array with a fixed number of items with different schemas. */
  case class STuple(tpe: Type, members: List[Schema]) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  /** An object with a fixed number of attributes, each having a different schema.
    *
    * @param annotations reified annotations from the corresponding Scala type.
    *                    This allows annotations to be used easily to modify generated schemas,
    *                    without having to use reflection to access them.
    *                    This is filled by Autogen.apply with annotations on the class.
    */
  case class SObject(tpe: Type, members: List[SObjectMember], annotations: List[annotation.Annotation]) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  /** @param annotations reified annotations from the corresponding Scala type.
    *                    This allows annotations to be used easily to modify generated schemas,
    *                    without having to use reflection to access them.
    *                    This is filled by Autogen.apply with annotations on the constructor parameter.
    * @param default     The value used when unpickling if this object member is missing.
    */
  case class SObjectMember(name: String, schema: Schema, required: Boolean, annotations: List[annotation.Annotation],
                           default: Option[Any])

  /** An object with any number of attributes with different names, all sharing the same value schema. */
  case class SDict(tpe: Type, members: Schema) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  /** @param annotations reified annotations from the corresponding Scala type.
    *                    This allows annotations to be used easily to modify generated schemas,
    *                    without having to use reflection to access them.
    *                    This is filled by Autogen.children with annotations on the parent type.
    */
  case class SOneOf(tpe: Type, options: List[Schema], annotations: List[annotation.Annotation]) extends Schema {
    override def withTpe(tpe: Type): Schema = copy(tpe = tpe)
  }

  /** A reference is equivalent to the schema returned by `target`, but prevents loops in recursive or mutually recursive
    * schema definitions.
    *
    * `tpe` and `resolve` are lazy vals to prevent cases where `toTpe` or `target` return different values on subsequent
    * calls; this interferes with e.g. code in JsonSchema that wants to compare Schemas for equality.
    *
    * The attributes are private to prevent them from being called directly, bypassing the lazy vals.
    */
  case class SRef(private val toTpe: () => Type, private val target: () => Schema) extends Schema {
    override lazy val tpe: Type = toTpe()
    override def withTpe(tpe: Type): Schema = copy(toTpe = () => tpe)

    lazy val resolve: Schema = target() match {
      case ref: SRef => ref.resolve
      case other => other
    }
  }
}
