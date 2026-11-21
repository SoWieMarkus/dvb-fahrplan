package markus.wieland.dvbfahrplan.core.geo

import markus.wieland.dvbfahrplan.core.model.LatLng
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Converts Gauss-Krüger coordinates (Potsdam datum, as used by the VVO API) to WGS84.
 *
 * Port of the conversion by Helmut H. Heimeier:
 * Copyright (c) 2006, HELMUT H. HEIMEIER. Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the
 * Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of the Software.
 */
object GaussKrueger {

    private const val DEG = 180 / Math.PI
    private const val RAD = Math.PI / 180

    /**
     * @param right Rechtswert (easting, e.g. 4621685 in Dresden)
     * @param high Hochwert (northing, e.g. 5657497 in Dresden)
     * @return WGS84 position, or null if the values are outside the German GK system (e.g. 0|0 placeholders)
     */
    fun toWgs84(right: Double, high: Double): LatLng? {
        val (lp, bp) = toPotsdam(right, high) ?: return null
        return potsdamToWgs84(lp, bp)
    }

    fun toWgs84(right: Long, high: Long): LatLng? = toWgs84(right.toDouble(), high.toDouble())

    /** GK → geographic coordinates on the Bessel ellipsoid (Potsdam datum). Returns (longitude, latitude). */
    private fun toPotsdam(rw: Double, hw: Double): Pair<Double, Double>? {
        val a = 6377397.155
        val f = 3.342773154e-3
        val c = a / (1 - f)

        val ex2 = (2 * f - f * f) / ((1 - f) * (1 - f))
        val ex4 = ex2 * ex2
        val ex6 = ex4 * ex2
        val ex8 = ex4 * ex4

        val e0 = c * RAD * (1 - 3 * ex2 / 4 + 45 * ex4 / 64 - 175 * ex6 / 256 + 11025 * ex8 / 16384)
        val f2 = DEG * (3 * ex2 / 8 - 3 * ex4 / 16 + 213 * ex6 / 2048 - 255 * ex8 / 4096)
        val f4 = DEG * (21 * ex4 / 256 - 21 * ex6 / 256 + 533 * ex8 / 8192)
        val f6 = DEG * (151 * ex6 / 6144 - 453 * ex8 / 12288)

        val sigma = hw / e0
        val sigmr = sigma * RAD
        val bf = sigma + f2 * sin(2 * sigmr) + f4 * sin(4 * sigmr) + f6 * sin(6 * sigmr)

        val br = bf * RAD
        val tan1 = tan(br)
        val tan2 = tan1 * tan1
        val tan4 = tan2 * tan2
        val cos1 = cos(br)
        val cos2 = cos1 * cos1
        val etasq = ex2 * cos2

        val nd = c / sqrt(1 + etasq)
        val nd2 = nd * nd
        val nd3 = nd2 * nd
        val nd4 = nd2 * nd2
        val nd5 = nd4 * nd
        val nd6 = nd4 * nd2

        val zone = (rw / 1e6).toInt()
        val lh = zone * 3.0
        val dy = rw - (zone * 1e6 + 500000)
        val dy2 = dy * dy
        val dy3 = dy2 * dy
        val dy4 = dy2 * dy2
        val dy5 = dy4 * dy
        val dy6 = dy3 * dy3

        val b2 = -tan1 * (1 + etasq) / (2 * nd2)
        val b4 = tan1 * (5 + 3 * tan2 + 6 * etasq * (1 - tan2)) / (24 * nd4)
        val b6 = -tan1 * (61 + 90 * tan2 + 45 * tan4) / (720 * nd6)

        val l1 = 1 / (nd * cos1)
        val l3 = -(1 + 2 * tan2 + etasq) / (6 * nd3 * cos1)
        val l5 = (5 + 28 * tan2 + 24 * tan4) / (120 * nd5 * cos1)

        val bp = bf + DEG * (b2 * dy2 + b4 * dy4 + b6 * dy6)
        val lp = lh + DEG * (l1 * dy + l3 * dy3 + l5 * dy5)

        if (lp < 5 || lp > 16 || bp < 46 || bp > 56) return null
        return lp to bp
    }

    /** Shifts the datum from Potsdam (Bessel) to WGS84 (dx = 587 m, dy = 16 m, dz = 393 m). */
    private fun potsdamToWgs84(lp: Double, bp: Double): LatLng {
        val a = 6378137.000 - 739.845
        val fq = 3.35281066e-3 - 1.003748e-05
        val f = 3.35281066e-3

        val e2q = 2 * fq - fq * fq
        val e2 = 2 * f - f * f

        val b1 = bp * RAD
        val l1 = lp * RAD
        val nd = a / sqrt(1 - e2q * sin(b1) * sin(b1))

        val x = nd * cos(b1) * cos(l1) + 587.0
        val y = nd * cos(b1) * sin(l1) + 16.0
        val z = (1 - e2q) * nd * sin(b1) + 393.0

        val rb = sqrt(x * x + y * y)
        val lat = DEG * atan((z / rb) / (1 - e2))
        val base = DEG * atan(y / x)
        val lng = when {
            x > 0 -> base
            x < 0 && y > 0 -> base + 180
            x < 0 && y < 0 -> base - 180
            else -> 0.0
        }
        return LatLng(lat, lng)
    }
}
