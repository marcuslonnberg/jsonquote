package net.maffoo.jsonquote

import _root_.play.api.libs.json._
import scala.language.experimental.macros
import scala.reflect.macros.Context

package object play {
  implicit class RichJsonSringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): JsValue = macro jsonImpl
  }

  def jsonImpl(c: Context)(args: c.Expr[Any]*): c.Expr[JsValue] = {
    import c.universe._

    // convert the given json AST to a tree with arguments spliced in at the correct locations
    def splice(js: JsValue)(implicit args: Iterator[Tree]): Tree = js match {
      case SpliceValue()  => spliceValue(args.next)
      case SpliceValues() => c.abort(c.enclosingPosition, "cannot splice values at top level")

      case JsObject(members) =>
        val ms = members.map {
          case SpliceField()      => q"Seq(${spliceField(args.next)})"
          case SpliceFields()     => spliceFields(args.next)
          case SpliceFieldName(v) => q"Seq((${spliceFieldName(args.next)}, ${splice(v)}))"
          case (k, v)             => q"Seq(($k, ${splice(v)}))"
        }
        q"JsObject(IndexedSeq(..$ms).flatten)"

      case JsArray(elements) =>
        val es = elements.map {
          case SpliceValue()  => q"Seq(${spliceValue(args.next)})"
          case SpliceValues() => spliceValues(args.next)
          case e              => q"Seq(${splice(e)})"
        }
        q"JsArray(IndexedSeq(..$es).flatten)"

      case JsString(s)      => q"JsString($s)"
      case JsNumber(n)      => q"JsNumber(BigDecimal(${n.toString}))"
      case JsBoolean(true)  => q"JsBoolean(true)"
      case JsBoolean(false) => q"JsBoolean(false)"
      case JsNull           => q"JsNull"
    }

    def spliceValue(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[JsValue] => e
      case t =>
        q"implicitly[Writes[$t]].writes($e)"
    }

    def spliceValues(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[JsValue]] => e
      case t if t <:< c.typeOf[Iterable[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.map($writer.writes)"

      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[JsValue]] => e
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"Option.option2Iterable($e).map($writer.writes)"

      case t => c.abort(e.pos, s"required Iterable[_] but got $t")
    }

    def spliceField(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[(String, JsValue)] => e
      case t if t <:< c.typeOf[(String, Any)] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[(String, Nothing)] :: Nil))(1)
        val writer = inferWriter(e, valueTpe)
        q"val (k, v) = $e; (k, $writer.writes(v))"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFields(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[(String, JsValue)]] => e
      case t if t <:< c.typeOf[Iterable[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"$e.map { case (k, v) => (k, $writer.writes(v)) }"

      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[(String, JsValue)]] => e
      case t if t <:< c.typeOf[Option[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"Option.option2Iterable($e).map { case (k, v) => (k, $writer.writes(v)) }"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFieldName(e: Tree): Tree = e.tpe match {
      case t if t =:= c.typeOf[String] => e
      case t => c.abort(e.pos, s"required String but got $t")
    }

    // return a list of type parameters in the given type
    // example: List[(String, Int)] => Seq(Tuple2, String, Int)
    def typeParams(tpe: Type): Seq[Type] = {
      val b = Iterable.newBuilder[Type]
      tpe.foreach(b += _)
      b.result.drop(2).grouped(2).map(_.head).toIndexedSeq
    }

    // locate an implicit Writes[T] for the given type
    def inferWriter(e: Tree, t: Type): Tree = {
      val writerTpe = appliedType(c.typeOf[Writes[_]], List(t))
      c.inferImplicitValue(writerTpe) match {
        case EmptyTree => c.abort(e.pos, s"could not find implicit value of type Writes[$t]")
        case tree => tree
      }
    }

    // Parse the string context parts into a json AST with holes, and then
    // typecheck/convert args to the appropriate types and splice them in.
    c.prefix.tree match {
      case Apply(_, List(Apply(_, partTrees))) =>
        val parts = partTrees map { case Literal(Constant(const: String)) => const }
        val js = Parse(parts)
        c.Expr[JsValue](splice(js)(args.iterator.map(_.tree)))

      case _ =>
        c.abort(c.enclosingPosition, "invalid")
    }
  }
}