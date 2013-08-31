package net.maffoo.jsonquote.spray

import net.maffoo.jsonquote.Parser
import _root_.spray.json._

object Parse extends Parser[JsValue, (String, JsValue)] {
  def makeObject(fields: Iterable[(String, JsValue)]): JsValue = JsObject(fields.toSeq: _*)
  def makeArray(elements: Iterable[JsValue]): JsValue = JsArray(elements.toSeq: _*)
  def makeNumber(n: BigDecimal): JsValue = JsNumber(n)
  def makeString(s: String): JsValue = JsString(s)
  def makeBoolean(b: Boolean): JsValue = JsBoolean(b)
  def makeNull(): JsValue = JsNull
  def makeSpliceValue(): JsValue = SpliceValue()
  def makeSpliceValues(): JsValue = SpliceValues()

  def makeField(k: String, v: JsValue): (String, JsValue) = (k, v)
  def makeSpliceField(): (String, JsValue) = SpliceField()
  def makeSpliceFields(): (String, JsValue) = SpliceFields()
  def makeSpliceFieldName(v: JsValue): (String, JsValue) = SpliceFieldName(v)
}