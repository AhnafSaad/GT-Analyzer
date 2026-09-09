package com.example

import com.example.data.NetworkUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testParseSecondHop_PrivateIp() {
        // User's exact Hop 2 line from actual traceroute
        val line = "From 192.168.112.1: icmp_seq=1 Time to live exceeded"
        val result = NetworkUtils.parseTracerouteOutputLine(
            line = line,
            target = "8.8.8.8",
            probeTtl = 2,
            elapsedMs = 2.0
        )
        assertNotNull(result)
        assertEquals(2, result?.hopNumber) // Hop is strictly probe TTL
        assertEquals("192.168.112.1", result?.ip)
        assertFalse(result?.isTargetReached ?: true)
    }

    @Test
    fun testParseHop_CgnatOrPrivateSubnet() {
        val line = "From 10.136.91.233: icmp_seq=1 Time to live exceeded time=3.5 ms"
        val result = NetworkUtils.parseTracerouteOutputLine(
            line = line,
            target = "8.8.8.8",
            probeTtl = 3,
            elapsedMs = 3.5
        )
        assertNotNull(result)
        assertEquals(3, result?.hopNumber)
        assertEquals("10.136.91.233", result?.ip)
        assertEquals(3.5, result?.latencyMs ?: 0.0, 0.1)
    }

    @Test
    fun testParseHop_PublicIp() {
        val line = "From 14.1.100.234: time to live exceeded"
        val result = NetworkUtils.parseTracerouteOutputLine(
            line = line,
            target = "8.8.8.8",
            probeTtl = 6,
            elapsedMs = 20.1
        )
        assertNotNull(result)
        assertEquals(6, result?.hopNumber)
        assertEquals("14.1.100.234", result?.ip)
    }

    @Test
    fun testParseTargetReached_DifferentTtlInReplyHeader() {
        // Notice: The reply header contains ttl=115, but probeTtl is 10.
        // Hop number must be strictly 10, never 115!
        val line = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=115 time=24.2 ms"
        val result = NetworkUtils.parseTracerouteOutputLine(
            line = line,
            target = "8.8.8.8",
            probeTtl = 10,
            elapsedMs = 24.2
        )
        assertNotNull(result)
        assertEquals(10, result?.hopNumber) // strictly probe TTL
        assertEquals("8.8.8.8", result?.ip)
        assertEquals(24.2, result?.latencyMs ?: 0.0, 0.1)
        assertTrue(result?.isTargetReached ?: false)
    }

    @Test
    fun testIgnorePingHeaderAndStats() {
        val header = "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data."
        val stats = "1 packets transmitted, 0 received, 100% packet loss, time 0ms"
        assertNull(NetworkUtils.parseTracerouteOutputLine(header, "8.8.8.8", 1, 10.0))
        assertNull(NetworkUtils.parseTracerouteOutputLine(stats, "8.8.8.8", 1, 10.0))
    }

    @Test
    fun testPartialResponse_Hop2CapturedReliably() {
        // Simulating packet 1 lost, packet 2 returning 10.136.91.233
        val lines = listOf(
            "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.",
            "From 10.136.91.233: icmp_seq=2 Time to live exceeded",
            "--- 8.8.8.8 ping statistics ---",
            "3 packets transmitted, 1 received, 66% packet loss, time 2000ms"
        )
        val captured = lines.mapNotNull {
            NetworkUtils.parseTracerouteOutputLine(it, "8.8.8.8", probeTtl = 2, elapsedMs = 12.4)
        }.firstOrNull()

        assertNotNull("Expected Hop 2 to be parsed from partial response", captured)
        assertEquals(2, captured?.hopNumber)
        assertEquals("10.136.91.233", captured?.ip)
        assertFalse(captured?.isTargetReached ?: true)
    }

    @Test
    fun testPartialResponse_VariousRouterReplyFormats() {
        val formats = listOf(
            "From 10.136.91.233 icmp_seq=1 Time to live exceeded",
            "From 10.136.91.233: Time to live exceeded",
            "92 bytes from 10.136.91.233: Time to live exceeded",
            "From 10.136.91.233: icmp_seq=3 Time Exceeded"
        )
        for (fmt in formats) {
            val res = NetworkUtils.parseTracerouteOutputLine(fmt, "8.8.8.8", probeTtl = 2, elapsedMs = 8.0)
            assertNotNull("Failed to parse format: $fmt", res)
            assertEquals("10.136.91.233", res?.ip)
            assertEquals(2, res?.hopNumber)
        }
    }
}
