package com.example.localization

enum class AppLanguage(val code: String, val label: String) {
    BN("bn", "বাংলা"),
    EN("en", "English")
}

object Translations {
    private val strings = mapOf(
        "appName" to mapOf(AppLanguage.BN to "GT Wifi Analyzer", AppLanguage.EN to "GT Wifi Analyzer"),
        "dashboard" to mapOf(AppLanguage.BN to "ড্যাশবোর্ড", AppLanguage.EN to "Dashboard"),
        "gaming" to mapOf(AppLanguage.BN to "গেমিং", AppLanguage.EN to "Gaming"),
        "dns" to mapOf(AppLanguage.BN to "ডিএনএস", AppLanguage.EN to "DNS Check"),
        "nearby" to mapOf(AppLanguage.BN to "আশেপাশে", AppLanguage.EN to "Nearby"),
        "speed" to mapOf(AppLanguage.BN to "স্পিড", AppLanguage.EN to "Speed"),
        "diagnostic" to mapOf(AppLanguage.BN to "ডায়াগনস্টিক", AppLanguage.EN to "Diagnostic"),
        "runDiagnostic" to mapOf(AppLanguage.BN to "ডায়াগনস্টিক চালান", AppLanguage.EN to "Run Diagnostic"),
        "running" to mapOf(AppLanguage.BN to "চলছে...", AppLanguage.EN to "Running..."),
        "homeRouter" to mapOf(AppLanguage.BN to "হোম রাউটার", AppLanguage.EN to "Home Router"),
        "ispRouter" to mapOf(AppLanguage.BN to "আইএসপি রাউটার", AppLanguage.EN to "ISP Router"),
        "localGateway" to mapOf(AppLanguage.BN to "লোকাল গেটওয়ে", AppLanguage.EN to "Local Gateway"),
        "upstreamGateway" to mapOf(AppLanguage.BN to "আপস্ট্রিম গেটওয়ে", AppLanguage.EN to "Upstream Gateway"),
        "ipAddress" to mapOf(AppLanguage.BN to "আইপি অ্যাড্রেস", AppLanguage.EN to "IP Address"),
        "hopNumber" to mapOf(AppLanguage.BN to "হপ নম্বর", AppLanguage.EN to "Hop Number"),
        "latency" to mapOf(AppLanguage.BN to "লেটেন্সি", AppLanguage.EN to "Latency"),
        "diagnosticInfo" to mapOf(AppLanguage.BN to "এই তথ্য কীভাবে কাজ করে?", AppLanguage.EN to "How does this work?"),
        "diagnosticExplain" to mapOf(
            AppLanguage.BN to "ট্রেসরুট ও পিং প্রটোকল ব্যবহার করে নেটওয়ার্ক পাথ বিশ্লেষণ করা হয়। Gateway 1 হলো লোকাল হোম রাউটার। WAN বা আইএসপি গেটওয়ে নিশ্চিত না হলে 'Unknown' দেখানো হয় এবং ট্রেসরুটকে কেবল পাথ অ্যানালাইসিসের জন্য ব্যবহার করা হয়।",
            AppLanguage.EN to "Probes and traceroute analyze path behavior. Gateway 1 is your local router. The actual upstream WAN gateway is verified via discovery protocols or reported as Unknown, using traceroute strictly for path analysis."
        ),
        "gatewayNotFound" to mapOf(AppLanguage.BN to "গেটওয়ে পাওয়া যায়নি", AppLanguage.EN to "Gateway not found"),
        "diagnosticFailed" to mapOf(AppLanguage.BN to "ডায়াগনস্টিক ব্যর্থ হয়েছে", AppLanguage.EN to "Diagnostic failed"),
        "yourDevice" to mapOf(AppLanguage.BN to "আপনার ডিভাইস", AppLanguage.EN to "Your Device"),
        "internet" to mapOf(AppLanguage.BN to "ইন্টারনেট", AppLanguage.EN to "Internet"),
        "ping" to mapOf(AppLanguage.BN to "পিং", AppLanguage.EN to "Ping"),
        "jitter" to mapOf(AppLanguage.BN to "জিটার", AppLanguage.EN to "Jitter"),
        "packetLoss" to mapOf(AppLanguage.BN to "প্যাকেট লস", AppLanguage.EN to "Packet Loss"),
        "signalStrength" to mapOf(AppLanguage.BN to "সিগন্যাল ক্ষমতা", AppLanguage.EN to "Signal Strength"),
        "frequency" to mapOf(AppLanguage.BN to "ফ্রিকোয়েন্সি", AppLanguage.EN to "Frequency"),
        "linkSpeed" to mapOf(AppLanguage.BN to "লিঙ্ক স্পিড", AppLanguage.EN to "Link Speed"),
        "verdict" to mapOf(AppLanguage.BN to "সার্বিক অবস্থা", AppLanguage.EN to "Overall Verdict"),
        "excellent" to mapOf(AppLanguage.BN to "চমৎকার", AppLanguage.EN to "Excellent"),
        "good" to mapOf(AppLanguage.BN to "ভালো", AppLanguage.EN to "Good"),
        "fair" to mapOf(AppLanguage.BN to "মোটামুটি", AppLanguage.EN to "Fair"),
        "poor" to mapOf(AppLanguage.BN to "দুর্বল", AppLanguage.EN to "Poor"),
        "smooth" to mapOf(AppLanguage.BN to "স্মুথ", AppLanguage.EN to "Smooth"),
        "playable" to mapOf(AppLanguage.BN to "খেলার উপযোগী", AppLanguage.EN to "Playable"),
        "laggy" to mapOf(AppLanguage.BN to "ল্যাগি", AppLanguage.EN to "Laggy"),
        "stable" to mapOf(AppLanguage.BN to "স্থিতিশীল", AppLanguage.EN to "Stable"),
        "slightlySlow" to mapOf(AppLanguage.BN to "সামান্য ধীর", AppLanguage.EN to "Slightly Slow"),
        "unstable" to mapOf(AppLanguage.BN to "অস্থিতিশীল", AppLanguage.EN to "Unstable"),
        "bestRoute" to mapOf(AppLanguage.BN to "সেরা রুট", AppLanguage.EN to "Best Route"),
        "hosts" to mapOf(AppLanguage.BN to "হোস্টসমূহ", AppLanguage.EN to "Hosts"),
        "startTest" to mapOf(AppLanguage.BN to "টেস্ট শুরু করুন", AppLanguage.EN to "Start Speed Test"),
        "stopTest" to mapOf(AppLanguage.BN to "টেস্ট থামান", AppLanguage.EN to "Stop Test"),
        "testing" to mapOf(AppLanguage.BN to "পরিমাপ চলছে...", AppLanguage.EN to "Testing..."),
        "download" to mapOf(AppLanguage.BN to "ডাউনলোড", AppLanguage.EN to "Download"),
        "upload" to mapOf(AppLanguage.BN to "আপলোড", AppLanguage.EN to "Upload"),
        "scanNetworks" to mapOf(AppLanguage.BN to "স্ক্যান করুন", AppLanguage.EN to "Scan Networks"),
        "scanning" to mapOf(AppLanguage.BN to "স্ক্যান হচ্ছে...", AppLanguage.EN to "Scanning..."),
        "noNetworksFound" to mapOf(AppLanguage.BN to "কোনো ওয়াইফাই পাওয়া যায়নি", AppLanguage.EN to "No networks found"),
        "locationPermissionRequired" to mapOf(
            AppLanguage.BN to "ওয়াইফাই স্ক্যান ও এসএসআইডি দেখার জন্য লোকেশন পারমিশন প্রয়োজন।",
            AppLanguage.EN to "Location permission is required on Android to scan nearby WiFi networks."
        ),
        "grantPermission" to mapOf(AppLanguage.BN to "পারমিশন দিন", AppLanguage.EN to "Grant Permission"),
        "backendNotice" to mapOf(
            AppLanguage.BN to "ব্যাকএন্ড সার্ভার (10.26.8.122:3000) অফলাইন। লোকাল ডিভাইস টপোলজি ও প্রব ব্যবহার করা হচ্ছে।",
            AppLanguage.EN to "Backend server (10.26.8.122:3000) unreachable. Using on-device local topology probe."
        ),
        "serverSettings" to mapOf(AppLanguage.BN to "সার্ভার কনফিগ", AppLanguage.EN to "Server Config"),
        "serverUrl" to mapOf(AppLanguage.BN to "সার্ভার URL", AppLanguage.EN to "Server URL"),
        "save" to mapOf(AppLanguage.BN to "সংরক্ষণ", AppLanguage.EN to "Save"),
        "cancel" to mapOf(AppLanguage.BN to "বাতিল", AppLanguage.EN to "Cancel"),
        "connectedWifi" to mapOf(AppLanguage.BN to "সংযুক্ত নেটওয়ার্ক", AppLanguage.EN to "Connected Network"),
        "history" to mapOf(AppLanguage.BN to "লাইভ গ্রাফ হিস্টোরি", AppLanguage.EN to "Live Graph History"),
        "gameLatency" to mapOf(AppLanguage.BN to "গেমিং সার্ভার লেটেন্সি", AppLanguage.EN to "Game Server Latency"),
        "dnsResolvers" to mapOf(AppLanguage.BN to "DoH রিজলভার পারফরম্যান্স", AppLanguage.EN to "DoH Resolver Performance"),
        "reload" to mapOf(AppLanguage.BN to "রিফ্রেশ", AppLanguage.EN to "Refresh"),
        "ms" to mapOf(AppLanguage.BN to "মি.সে.", AppLanguage.EN to "ms"),
        "mbps" to mapOf(AppLanguage.BN to "মেগাবিট/সে.", AppLanguage.EN to "Mbps"),
        "networkTopology" to mapOf(AppLanguage.BN to "নেটওয়ার্ক টপোলজি ইনফোগ্রাফিক", AppLanguage.EN to "Network Topology Infographic"),
        "healthScore" to mapOf(AppLanguage.BN to "রুট হেলথ স্কোর", AppLanguage.EN to "Route Health Score"),
        "latencyDistribution" to mapOf(AppLanguage.BN to "হপ লেটেন্সি বিশ্লেষণ", AppLanguage.EN to "Hop Latency Breakdown"),
        "packetJourney" to mapOf(AppLanguage.BN to "টিটিএল প্যাকেট জার্নি", AppLanguage.EN to "TTL Packet Journey"),
        "hop1WiFi" to mapOf(AppLanguage.BN to "হপ ১: হোম ওয়াইফাই", AppLanguage.EN to "Hop 1: Home WiFi"),
        "hop2ISP" to mapOf(AppLanguage.BN to "হপ ২: আইএসপি কোর", AppLanguage.EN to "Hop 2: ISP Core"),
        "hop3Cloud" to mapOf(AppLanguage.BN to "হপ ৩: ক্লাউড ব্যাকবোন", AppLanguage.EN to "Hop 3: Cloud Backbone"),
        "routeQuality" to mapOf(AppLanguage.BN to "রুট মান", AppLanguage.EN to "Route Quality"),
        "nodeDetails" to mapOf(AppLanguage.BN to "নোড বিস্তারিত", AppLanguage.EN to "Node Details"),
        "tapNodeHint" to mapOf(AppLanguage.BN to "বিস্তারিত দেখতে যেকোনো নোডে ট্যাপ করুন", AppLanguage.EN to "Tap any node above to inspect details"),
        "runAgain" to mapOf(AppLanguage.BN to "পুনরায় ডায়াগনস্টিক চালান", AppLanguage.EN to "Run Diagnostic Again"),
        "diagnosisVerdict" to mapOf(AppLanguage.BN to "ডায়াগনস্টিক সিদ্ধান্ত", AppLanguage.EN to "Diagnostic Verdict"),
        "diagnosisEvidence" to mapOf(AppLanguage.BN to "প্রমাণ ও পরিমাপ তালিকা", AppLanguage.EN to "Evidence & Measurements"),
        "upstreamUnknownNote" to mapOf(
            AppLanguage.BN to "ল্যান ক্লায়েন্টের কাছে রাউটার এর WAN গেটওয়ে আইপি উন্মুক্ত নয় (Unknown)",
            AppLanguage.EN to "WAN Next-Hop not exposed over LAN by router (Reported as Unknown)"
        ),
        "icmpRateLimitNote" to mapOf(
            AppLanguage.BN to "কিছু হপে টাইমআউট দেখানো স্বাভাবিক কারণ ট্রানজিট রাউটারগুলো ICMP TTL-expired মেসেজ সীমিত বা ড্রপ করে (RFC 1812)। এটি সংযোগ বিচ্যুতি নয়।",
            AppLanguage.EN to "Timeouts at certain hops are normal as transit routers deprioritize or block ICMP TTL-expired replies (RFC 1812). This is not packet loss."
        ),
        "tracerouteHops" to mapOf(AppLanguage.BN to "ট্রেসরুট পাথ হপসমূহ", AppLanguage.EN to "Traceroute Path Hops"),
        "upstreamInspection" to mapOf(AppLanguage.BN to "আপস্ট্রিম গেটওয়ে ডিবাগ ও পর্যবেক্ষণ", AppLanguage.EN to "Upstream Gateway Inspection & Debug"),
        "localIpLabel" to mapOf(AppLanguage.BN to "লোকাল আইপি", AppLanguage.EN to "Local IP"),
        "localGatewayLabel" to mapOf(AppLanguage.BN to "লোকাল গেটওয়ে", AppLanguage.EN to "Local Gateway"),
        "detectedUpstreamLabel" to mapOf(AppLanguage.BN to "শনাক্তকৃত আপস্ট্রিম গেটওয়ে", AppLanguage.EN to "Detected Upstream Gateway"),
        "detectionMethodLabel" to mapOf(AppLanguage.BN to "শনাক্তকরণ পদ্ধতি", AppLanguage.EN to "Detection Method"),
        "confidenceLabel" to mapOf(AppLanguage.BN to "বিশ্বাসযোগ্যতা মাত্রা", AppLanguage.EN to "Confidence Level"),
        "tracerouteFirstHopLabel" to mapOf(AppLanguage.BN to "ট্রেসরুট প্রথম হপ", AppLanguage.EN to "Traceroute First Hop"),
        "tracerouteCandidatesLabel" to mapOf(AppLanguage.BN to "সম্ভাব্য আপস্ট্রিম হপসমূহ", AppLanguage.EN to "Candidate Upstream Hops"),
        "reasoningEvidenceLabel" to mapOf(AppLanguage.BN to "যুক্তি ও প্রটোকল প্রমাণ লগ", AppLanguage.EN to "Reasoning & Evidence Log"),
        "confirmedGateway" to mapOf(AppLanguage.BN to "প্রমাণিত গেটওয়ে", AppLanguage.EN to "Confirmed Actual Gateway"),
        "inferredCandidate" to mapOf(AppLanguage.BN to "অনুমানকৃত সম্ভাব্য রাউটার", AppLanguage.EN to "Inferred Candidate Router"),
        "statusGood" to mapOf(AppLanguage.BN to "ভালো", AppLanguage.EN to "Good"),
        "statusProblem" to mapOf(AppLanguage.BN to "সমস্যা", AppLanguage.EN to "Problem"),
        "statusUnknown" to mapOf(AppLanguage.BN to "অজানা", AppLanguage.EN to "Unknown"),
        "workflowDiagram" to mapOf(AppLanguage.BN to "নেটওয়ার্ক ওয়ার্কফ্লো ডায়াগ্রাম", AppLanguage.EN to "Network Workflow Diagram"),
        "workflowSubtitle" to mapOf(
            AppLanguage.BN to "ডিভাইস ➔ লোকাল গেটওয়ে ➔ আপস্ট্রিম গেটওয়ে ➔ ইন্টারনেট",
            AppLanguage.EN to "Device ➔ Local Gateway ➔ Upstream Gateway ➔ Internet"
        )
    )

    fun tr(key: String, lang: AppLanguage): String {
        return strings[key]?.get(lang) ?: strings[key]?.get(AppLanguage.BN) ?: key
    }
}
