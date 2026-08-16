package io.worxbend.dotbot.config

/**
 * Conversion of the boxed Java numbers that the HOCON and TOML libraries hand back.
 *
 * Shared by both because both sit on Java libraries with the same numeric tower.
 */
private[config] object ConfigNumbers:
  def numberValue(value: java.lang.Number): BigDecimal =
    value match
      case item: java.lang.Byte       => BigDecimal(item.toLong)
      case item: java.lang.Short      => BigDecimal(item.toLong)
      case item: java.lang.Integer    => BigDecimal(item.toLong)
      case item: java.lang.Long       => BigDecimal(item.longValue())
      case item: java.lang.Float      => BigDecimal.decimal(item.toDouble)
      case item: java.lang.Double     => BigDecimal.decimal(item.doubleValue())
      case item: java.math.BigInteger => BigDecimal(item)
      case item: java.math.BigDecimal => BigDecimal(item)
      case other                      => BigDecimal(other.toString)
