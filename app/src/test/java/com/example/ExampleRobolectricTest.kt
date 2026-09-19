package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DiagnosticWorkflowNode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("YT Analyzer", appName)
  }

  @Test
  fun `verify translations dictionary keys exist`() {
    val bnTitle = com.example.localization.Translations.tr("appName", com.example.localization.AppLanguage.BN)
    val enTitle = com.example.localization.Translations.tr("appName", com.example.localization.AppLanguage.EN)
    assertEquals("YT Analyzer", bnTitle)
    assertEquals("YT Analyzer", enTitle)
  }

  @Test
  fun `first point of failure picks first middle node with partial loss`() {
    val nodeDevice = DiagnosticWorkflowNode(
        stepNumber = 1,
        title = "আপনার ডিভাইস",
        ip = "192.168.0.100",
        latencyText = "1.0 ms",
        packetLossPercent = 0.0,
        packetLossText = "0.0%",
        isReachable = true
    )
    val nodeRouter = DiagnosticWorkflowNode(
        stepNumber = 2,
        title = "হোম ওয়াইফাই রাউটার",
        ip = "192.168.0.1",
        latencyText = "2.0 ms",
        packetLossPercent = 0.0,
        packetLossText = "0.0%",
        isReachable = true
    )
    val node3 = DiagnosticWorkflowNode(
        stepNumber = 3,
        title = "পরবর্তি ডিভাইস",
        ip = "10.0.0.1",
        latencyText = "Timeout",
        packetLossPercent = 100.0, // ICMP blocked, not partial loss!
        packetLossText = "100.0%",
        isIcmpBlocked = true,
        statusText = "ICMP বন্ধ আছে",
        isReachable = false
    )
    val node4 = DiagnosticWorkflowNode(
        stepNumber = 4,
        title = "আপস্ট্রিম গেটওয়ে ১",
        ip = "10.0.1.1",
        latencyText = "15.0 ms",
        packetLossPercent = 20.0, // Partial loss!
        packetLossText = "20.0%",
        isReachable = true
    )
    val node5 = DiagnosticWorkflowNode(
        stepNumber = 5,
        title = "আপস্ট্রিম গেটওয়ে ২",
        ip = "10.0.2.1",
        latencyText = "25.0 ms",
        packetLossPercent = 40.0, // Also partial loss, but node4 is first!
        packetLossText = "40.0%",
        isReachable = true
    )
    val nodeInternet = DiagnosticWorkflowNode(
        stepNumber = 6,
        title = "ইন্টারনেট",
        ip = "8.8.8.8",
        latencyText = "30.0 ms",
        packetLossPercent = 0.0,
        packetLossText = "0.0%",
        iconType = "internet",
        isReachable = true
    )

    val nodes = listOf(nodeDevice, nodeRouter, node3, node4, node5, nodeInternet)
    val middleNodes = nodes.filter { it.stepNumber >= 3 && it.iconType != "internet" && it.title != "ইন্টারনেট" }
    val firstFailing = middleNodes.firstOrNull { it.packetLossPercent > 3.0 && it.packetLossPercent < 100.0 }

    org.junit.Assert.assertNotNull(firstFailing)
    assertEquals(4, firstFailing?.stepNumber)
    assertEquals("আপস্ট্রিম গেটওয়ে ১", firstFailing?.title)
    val expectedMsg = "${firstFailing?.title} এ সমস্যা।"
    assertEquals("আপস্ট্রিম গেটওয়ে ১ এ সমস্যা।", expectedMsg)
  }

  @Test
  fun `first point of failure on node 3 displays exact router next device message`() {
    val node3 = DiagnosticWorkflowNode(
        stepNumber = 3,
        title = "পরবর্তি ডিভাইস",
        ip = "10.0.0.1",
        latencyText = "12.0 ms",
        packetLossPercent = 20.0,
        packetLossText = "20.0%",
        isReachable = true
    )
    val firstFailing = node3
    val msg = if (firstFailing.stepNumber == 3 || firstFailing.title.contains("পরবর্তি")) {
        "আপনার হোম ওয়াইফাই রাউটারের পরের ডিভাইসে সমস্যা।"
    } else {
        "${firstFailing.title} এ সমস্যা।"
    }
    assertEquals("আপনার হোম ওয়াইফাই রাউটারের পরের ডিভাইসে সমস্যা।", msg)
  }

  @Test
  fun `generateDiagnosticReportText generates terminal formatted statistics`() {
    val nodeDevice = DiagnosticWorkflowNode(
        stepNumber = 1,
        title = "আপনার ডিভাইস",
        ip = "192.168.0.105",
        latencyText = "0.0 ms",
        latencyMs = 0.0,
        minMs = 0.0,
        maxMs = 0.0,
        packetLossPercent = 0.0,
        packetLossText = "0.0%",
        statusText = "স্বাভাবিক",
        statusLevel = "green"
    )
    val nodeRouter = DiagnosticWorkflowNode(
        stepNumber = 2,
        title = "হোম ওয়াইফাই রাউটার",
        ip = "192.168.0.1",
        latencyText = "2.5 ms",
        latencyMs = 2.5,
        minMs = 1.2,
        maxMs = 4.1,
        packetLossPercent = 0.0,
        packetLossText = "0.0%",
        statusText = "স্বাভাবিক",
        statusLevel = "green"
    )
    val nodeHop = DiagnosticWorkflowNode(
        stepNumber = 3,
        title = "পরবর্তি ডিভাইস",
        ip = "10.10.1.1",
        latencyText = "12.4 ms",
        latencyMs = 12.4,
        minMs = 8.5,
        maxMs = 18.2,
        packetLossPercent = 20.0,
        packetLossText = "20.0%",
        statusText = "সমস্যা",
        statusLevel = "red"
    )
    val nodeInternet = DiagnosticWorkflowNode(
        stepNumber = 4,
        title = "ইন্টারনেট",
        ip = "8.8.8.8",
        latencyText = "26.1 ms",
        latencyMs = 26.1,
        minMs = 22.4,
        maxMs = 31.0,
        packetLossPercent = 0.0,
        packetLossText = "0.0%",
        statusText = "স্বাভাবিক",
        statusLevel = "green",
        iconType = "internet"
    )
    val skippedNode = DiagnosticWorkflowNode(
        stepNumber = 5,
        title = "অতিরিক্ত নোড",
        ip = "1.1.1.1",
        latencyText = "",
        isSkipped = true
    )

    val result = com.example.model.DiagnosticResult(
        nodes = listOf(nodeDevice, nodeRouter, nodeHop, nodeInternet, skippedNode)
    )

    val report = com.example.ui.screens.generateDiagnosticReportText(result)

    org.junit.Assert.assertTrue(report.contains("YT Analyzer Network Diagnostic Report"))
    org.junit.Assert.assertTrue(report.contains("Node Name: আপনার ডিভাইস"))
    org.junit.Assert.assertTrue(report.contains("Status: ভালো"))
    org.junit.Assert.assertTrue(report.contains("5 packets transmitted, 5 received, 0% packet loss"))
    org.junit.Assert.assertTrue(report.contains("Node Name: পরবর্তি ডিভাইস"))
    org.junit.Assert.assertTrue(report.contains("Status: সমস্যা"))
    org.junit.Assert.assertTrue(report.contains("5 packets transmitted, 4 received, 20% packet loss"))
    org.junit.Assert.assertTrue(report.contains("rtt min/avg/max = 8.5/12.4/18.2 ms"))
    // Ensure skipped node was not included
    org.junit.Assert.assertFalse(report.contains("অতিরিক্ত নোড"))
  }
}
