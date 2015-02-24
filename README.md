# safepickle

A deliberately restricted pickling library for Scala. It has certain features, and deliberately lacks others, because it is tailormade for a particular scenario I needed. Most people will prefer general purpose libraries such as scala-pickling, upickle, or rapture, but this one is optimized for the following goals:

 1. Security. Pickled input can be generated by untrusted sources. Unpickling must not instantiate unexpected classes, take unpredictable amounts of space or time, or produce values not of the expected type. The set of pickleable types, and the code that serializes them, is determined at compile time, and runtime reflection is never used.
 2. Certain changes to the definitions of pickled types are guaranteed to be backward and forward compatible, so different versions of the program can communicate, and pickled data can be used for long term storage.
 3. Backward incompatible changes can be managed explicitly, with version numbers and conversion code, allowing new code to read data written by old code, and old code to fail on encountering data written by new code.
 4. Pickled classes correspond directly to the pickled form (at least for JSON and BSON), making it easy to write classes to represent data whose main schema definition is written in terms of the pickled format.

The library is also small enough to understand and validate by hand, and to make sure its performance is driven by that of the backend used (e.g. Jackson for JSON).

## Introduction to the main types

`safepickle` supports multiple backends which write data in different formats and using different implementations. It comes with a backend for JSON that uses Jackson and another for BSON that uses ReactiveMongo, and more can be added.

Every backend is required to support reading and writing these primitive types:
 
 * Boolean, String, Null
 * Int, Long, Float, Double
 * Array: a sequence of values of any types
 * Object: a map of strings to values of any types

A particular backend might support more primitives. For instance, BSON supports Binary (i.e. Array[Byte]) as a primitive type, but JSON doesn't. Other types are written as objects or arrays using primitive types.

A backend is declared by implementing `trait PicklingBackend`, which defines the types used (e.g. for JSON it might be a String), and provides a factory for Readers and Writers.

Implementations of `trait Reader` and `trait Writer` provide low-level access to reading and writing a sequence of primitive values in a particular backend. Users of `safepickle` normally don't interact directly with these interfaces, except for manually implementing Picklers.

The high-level trait for pickling looks like this (this is a simplified version):

```
trait Pickler[T, -Backend <: PicklingBackend] {
  def pickle(t: T, writer: Writer): Unit
  def unpickle(reader: Reader): T
}
```

A `Pickler` can read and write values of a type `T` to a particular `PicklingBackend`. This second parameter is important, because the same type might be written in different ways to different backends. For instance, an `Array[Byte]` might be written as the primitive Binary type to a BSON-based backend, and as a base64-encoded string to all other backends.

That is why the `Backend` type parameter is contravariant: if you can write to a particular backend, that doesn't mean you can write to all backends. But if you can write to the `PicklingBackend` type, you can write to any backend. This also allows implicit resolution to pick the most specific pickler available for your backend.

## Supported values for pickling

The primitive values corresponding to the above list (Boolean, String, Int, Long, Float, Double, and Null) are pickled directly as such.

An `Array[Byte]` is pickled as a base64 string, but some backends override this with, e.g. BSON-based backends have a dedicated Binary type.

Values of type `Iterable[T]`, where T is any supported type, are pickled as Arrays. This includes all kinds of `Iterable`s, including `Set`s, as well as `Array[T]`, which isn't natively an `Iterable[T]`.

Tuples of supported types.

`Map`s are serialized as arrays, where each key-value pair is pickled as an array of size 2. However, as a special case, `Map`s with String keys are pickled as Objects.

Objects are pickled as strings whose value is the object's (non fully qualified) name.

Classes are pickled as Objects whose attributes correspond to the values of the class's main constructor arguments.

Case objects and case classes behave the same way as ordinary objects and classes.

A `sealed trait T` is pickled as whichever of its (immediate) descendants is actually present. If that results in an Object, it will have an extra attribute named `$type`, equal to the class's name. This is known as the type tag, and tells the unpickler which value to instantiate. If pickling results in a String, because the concrete descendant of `trait T` is an object or a class with zero parameters, then the value of the string provides this service. Multiple sealed traits extending one another are flattened, so that only the final concrete class and its `$type` are written.

A `sealed abstract class` is treated the same as a `sealed trait`.

## Pickling classes

Picklers for classes are generated using the `Autogen` macro. The class must obey these requirements:

 * The primary constructor (the one that's written as part of the `class` definition) must have no more than one parameter list.
 * The primary constructor must be public.
 * The primary constructor's parameters must be of types that have implicit Picklers available.
 * The primary constructor's parameters must be declared as public `val`s or `var`s (using a case class does this by default).

Autogenerated class picklers have some special behaviors, designed to allow the compatible changes to class declarations that are listed in another section.

 1. If the class's primary constructor has no parameter lists, or a single empty parameter list, it's not pickled as an Object, but as a String whose value is the name of the class, the same as a Scala `object`.
 2. When unpickling, the order of the pickled Object's attributes doesn't have to correspond to the order of the constructor parameters. However, the `$version` attribute (if present) must come first.
 3. When unpickling, attributes with unexpected names are discarded.
 4. When pickling, if a parameter's value is equal to its declared default value, that parameter is not written. Equality is determined using == (i.e. the `equals` method).
 5. When unpickling, if a constructor parameter with a declared default value is missing a pickled value, the default value will be used instead.
 6. Class parameters of type `Option[T]` are pickled as follows: for `Some[T]`, the `T` value is written directly; for `None`, no attribute is written at all. This allows making an existing class parameter optional, which is a common change.

## Types not supported out of the box

It's always possible to write a Pickler manually for any unsuppored type.

 * Classes whose primary constructor has more than one parameter list, or is not public
 * Classes whose primary constructor has a parameter whose type isn't pickleable
 * Iterables whose static member type isn't pickleable (there's no Pickler available for it)

## Compatible type changes

As long as all object and class picklers used are created by the `Autogen` macro, or are written to be compatible with the above rules, the following changes to Scala definitions will be backward and forward compatible, so that different code versions will be able to exchange data.

Because the compatibility is bidirectional, each of these cases implies the reverse transformation is also compatible.

 1. These are all interchangeable: `object O`, `class O` and `class O()`. Also, case and non-case objects and classes are interchangeable.
 2. A class parameter can be added (at any position), if it has a default value declared. A parameter can be removed (from any position), if it previously (always) had a default value declared. When code with the parameter declared unpickles data without it, it uses the default value. When code without the parameter declared unpickles data with it, it ignores it.
 3. The order of parameters can be changed freely.
 4. Any sequence type (`Iterable[T]` or a subtype of it) can be replaced with any other sequence type with the same member type. E.g., `List[Int]`  can be replaced with `Vector[Int]`. This includes `Set`s and `Map`s, but not `Map`s whose key type is String, because those are pickled as Objects and not as Arrays. (If a non-Set sequence type is replaced with a Set, when unpickling, duplicate values will be discarded.)
 5. Only in a class parameter type, `T` can be replaced with `Option[T]`, as long as the non-optional `T` has always had a default value. (Recall that, for class parameters, `Option[T]` is written as a `T` or omitted entirely if the value was `None`.)
 6. A sequence member type, or a map key or value type, can be replaced with another type, if the two types are compatible according to these rules. For instance, `List[List[Int]]` can be replaced with `Vector[Set[Long]]`.

Extra TODOs (will be moved to tickets):
- Performance tests
- Document backend.picklers cake and add usage examples to readme, including @Name
- Versioning support
- Reduce the number of classes & objects created. 1) Pickle-able types can declare a pickler in their companion object,
  and then it won't be autogenerated every single time that type is included by some other type. TODO provide a mixin
  trait for the companion object to make this easier. 2) Primitive, collection and tuple picklers are defined and
  instantiated by every pickler that mixes in those traits, which includes every autogenerated pickler. They must
  stop being traits!
