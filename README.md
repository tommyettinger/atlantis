# atlantis
Ordered JDK Map and Set classes that allow lookup at an index

## Installation

With Gradle (may need `api` instead of `implementation`, or `compile` for old Gradle):
```groovy
implementation 'com.github.tommyettinger:atlantis:0.0.1'
```

Or with Maven:
```xml
<dependency>
  <groupId>com.github.tommyettinger</groupId>
  <artifactId>atlantis</artifactId>
  <version>0.0.1</version>
</dependency>
```

Or you can [use JitPack using its instructions](https://jitpack.io/#tommyettinger/atlantis).

## Usage

You have IndexedMap and IndexedSet now! These are quite full-featured collections that are similar
to the existing JDK classes LinkedHashMap and LinkedHashSet; all of these are insertion-ordered
but otherwise act like HashMap. Except, IndexedMap and IndexedSet allow lookup by index in constant
time, which removes the need to make iterators, and allow offline sorting of their entries by key
or by value. There's some other features too, like `alter()` to change a key without changing its
position in the order (or its value). Mostly, these are like a regular Map, with `keyAt()`,
`getAt()`, `removeAt()`, and so on added to operate *at* a given index (hence the library name).

Many of these features are already in libGDX's OrderedMap and OrderedSet classes, but neither of
those implements any JDK interface, so they aren't very interoperable.

## Licence

Apache 2.0, see [LICENSE](LICENSE).
