package com.gpo.scalepaw.data.transfer

import com.gpo.scalepaw.data.model.Measurement
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.MetricValue
import com.gpo.scalepaw.data.model.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CsvTransferTest {
    private val utc = ZoneOffset.UTC
    private val types = Metrics.builtins.mapIndexed { i, spec -> MetricType.seed(spec, i).copy(id = i + 1L) }
    private fun id(identity: String) = types.first { it.identity == identity }.id
    private val weight = id(Metrics.WEIGHT.identity)
    private val fat = id(Metrics.BODY_FAT.identity)
    private val heart = id(Metrics.HEART_RATE.identity)
    private val comment = id(Metrics.COMMENT.identity)
    private fun millis(text: String) = LocalDateTime.parse(text).toInstant(utc).toEpochMilli()

    @Test
    fun should_round_trip_when_exporting_then_importing() {
        val m = Measurement(
            userId = 1,
            timestamp = millis("2025-03-01T07:45:12"),
            values = mapOf(
                weight to MetricValue.of(80.5f),
                heart to MetricValue.of(62),
                comment to MetricValue.of("after run, \"tired\""),
            ),
        )
        val csv = CsvTransfer.encode(listOf(m), types, utc)
        val decoded = CsvTransfer.decode(csv, types, utc)
        assertEquals(1, decoded.rows.size)
        val (time, values) = decoded.rows.single()
        assertEquals(m.timestamp, time)
        assertEquals(m.values, values)
        assertTrue(decoded.skippedColumns.isEmpty())
    }

    @Test
    fun should_use_openscale_headers_when_exporting() {
        val header = CsvTransfer.encode(emptyList(), types, utc).lineSequence().first().split(',')
        assertEquals(listOf("DATE", "TIME"), header.take(2))
        assertTrue("WEIGHT" in header)
        assertTrue("BODY_FAT" in header)
        assertTrue("HEART_RATE" in header)
    }

    @Test
    fun should_import_openscale_export_when_columns_unknown_or_out_of_order() {
        val csv = "\uFEFFBODY_FAT,DATE,SCHRITTE,TIME,WEIGHT\r\n" +
            "21.4,2024-11-02,8000,06:30:00,79.9\r\n" +
            "\r\n" +
            ",2024-11-03,,06:31,80.1\r\n"
        val decoded = CsvTransfer.decode(csv, types, utc)
        assertEquals(listOf("SCHRITTE"), decoded.skippedColumns)
        assertEquals(2, decoded.rows.size)
        assertEquals(millis("2024-11-02T06:30:00"), decoded.rows[0].first)
        assertEquals(21.4f, decoded.rows[0].second[fat]!!.float!!, 0f)
        assertEquals(80.1f, decoded.rows[1].second[weight]!!.float!!, 0f)
        assertEquals(null, decoded.rows[1].second[fat])
    }

    @Test
    fun should_parse_legacy_date_time_column_when_present() {
        val decoded = CsvTransfer.decode("dateTime,weight\n2019-05-04 21:10,\"72,5\"\n", types, utc)
        assertEquals(millis("2019-05-04T21:10:00"), decoded.rows.single().first)
        assertEquals(72.5f, decoded.rows.single().second[weight]!!.float!!, 0f)
    }

    @Test
    fun should_skip_rows_when_date_unparseable_or_values_empty() {
        val csv = "DATE,TIME,WEIGHT\n03/11/2024,08:00,80\n2024-11-04,08:00,\n2024-11-05,,81\n"
        val decoded = CsvTransfer.decode(csv, types, utc)
        assertEquals(2, decoded.skippedRows)
        assertEquals(millis("2024-11-05T12:00:00"), decoded.rows.single().first)
    }

    @Test(expected = IllegalArgumentException::class)
    fun should_reject_file_when_no_date_column() {
        CsvTransfer.decode("WEIGHT\n80\n", types, utc)
    }

    @Test
    fun should_keep_quoted_newlines_when_parsing() {
        val rows = Csv.parse("a,b\r\n\"x\ny\",\"say \"\"hi\"\"\"\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x\ny", "say \"hi\"")), rows)
    }
}
