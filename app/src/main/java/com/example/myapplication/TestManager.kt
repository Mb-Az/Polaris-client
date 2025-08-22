import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.telephony.SmsManager
import android.util.Log
import com.example.myapplication.ConfigurationManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.common.base.Converter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class TestManager(private val context: Context) {

    companion object {
        private const val TIMEOUT_MS = 10000 // 10-second timeout for SMS delivery
        const val SMS_SENT_ACTION = "SMS_SENT"
        const val SMS_DELIVERED_ACTION = "SMS_DELIVERED"
    }

    public fun CollectTestResult(configurationManager : ConfigurationManager) : TestResult{
        var throughputTest : Long? = null
        var pingTest : Long? = null
        var dnsTest : Long? = null
        var webTest : Long? = null
        var smsTest : Long? = null

        if(configurationManager.getThroughputIncluded())
            throughputTest = throughputTest(ConfigurationManager.getThroughputURL())
        if(configurationManager.getPingIncluded())
            pingTest = pingTestVerbose(configurationManager.getPingTestUrl(), 10000L).rttMs
        if(configurationManager.getDNSIncluded())
            dnsTest = dnsTest(ConfigurationManager.getDnsResolveDomain())
        if(configurationManager.getWebIncluded())
            webTest = webTest(configurationManager.getWebTestUrl())
        if(configurationManager.getSMSIncluded())
            smsTest = smsTest(context, configurationManager.getSMSPhoneNumber(), ConfigurationManager.getSMSText() + configurationManager.getTestInterval())

        return TestResult(
            ping = pingTest,
            web = webTest,
            dns = dnsTest,
            throughput = throughputTest,
            sms = smsTest
        )
    }

    public fun throughputTest(throughputURL: String): Long {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(throughputURL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.connect()

            val inputStream = connection.inputStream
            var bytesRead = 0L
            val buffer = ByteArray(4096)
            var bytes = inputStream.read(buffer)
            while (bytes != -1) {
                bytesRead += bytes
                bytes = inputStream.read(buffer)
            }
            connection.disconnect()

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            return if (duration > 0) {
                // Calculate rate in KB/s
                // (bytes / 1024) / (milliseconds / 1000)
                // which simplifies to (bytes * 1000) / (milliseconds * 1024)
                ((bytesRead.toDouble() * 1000.0) / (duration.toDouble() * 1024.0)).toLong()
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1L
        }
    }

    data class PingResult(
        val rttMs: Long,            // -1 = failed
        val method: String,         // "ping:/system/bin/ping", "tcp-connect", or "none"
        val rawOutput: String,      // stdout+stderr from ping or error message
        val exitCode: Int           // exit code of process (or -999 if timed out, -998 if not run)
    )
    fun pingTestVerbose(host: String, timeoutMs: Long = 5000L): PingResult {
        val pingCandidates = listOf("/system/bin/ping", "/system/xbin/ping", "/bin/ping", "ping")
        val timeRegex = Regex("""time=([\d.]+)""")

        for (path in pingCandidates) {
            try {
                val pb = ProcessBuilder(path, "-c", "1", host)
                pb.redirectErrorStream(true) // merge stderr into stdout
                val proc = pb.start()

                val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                val output = proc.inputStream.bufferedReader().use(BufferedReader::readText)
                val exit = if (finished) proc.exitValue() else -999

                Log.d("PingTest", "Tried $path, finished=$finished, exit=$exit, output=\n$output")

                // Try parse time regardless of exit code
                val match = timeRegex.find(output)
                if (match != null) {
                    val timeStr = match.groupValues[1]
                    val rtt = try { timeStr.toDouble().toLong() } catch (e: Exception) { -1L }
                    return PingResult(rtt, "ping:$path", output, exit)
                } else {
                    // Continue trying other ping paths
                }
            } catch (e: Exception) {
                Log.w("PingTest", "Exception running $path: ${e.message}", e)
                // try next candidate
            }
        }

        // If we get here, ping didn't give a parseable RTT. Try TCP connect fallback.
        val ports = listOf(443, 80)
        for (port in ports) {
            try {
                val socket = Socket()
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                val end = System.nanoTime()
                socket.close()
                val rtt = TimeUnit.NANOSECONDS.toMillis(end - start)
                val out = "tcp-connect to $host:$port succeeded (connect time ${rtt}ms)"
                Log.d("PingTest", out)
                return PingResult(rtt, "tcp-connect:$port", out, 0)
            } catch (e: Exception) {
                Log.w("PingTest", "tcp connect to $host:$port failed: ${e.message}")
                // try next port
            }
        }

        // All methods failed
        return PingResult(-1L, "none", "no usable ping binary and tcp connect failed", -1)
    }

    public fun webTest(webURL: String): Long {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(webURL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            val endTime = System.currentTimeMillis()

            return if (responseCode == HttpURLConnection.HTTP_OK) {
                endTime - startTime
            } else {
                -1L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1L
        }
    }

    public fun dnsTest(dnsURL: String): Long {
        return try {
            val startTime = System.currentTimeMillis()
            java.net.InetAddress.getByName(dnsURL)
            val endTime = System.currentTimeMillis()
            endTime - startTime
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    fun smsTest(context: Context, phoneNumber: String?, message: String): Long {
        // Check for a valid phone number as a prerequisite
        if (phoneNumber.isNullOrBlank()) {
            return -1L
        }

        val latch = CountDownLatch(1)
        val deliveryTime = AtomicLong(-1)

        // A unique action to avoid conflicts with other apps
        val smsDeliveredAction = "com.yourapp.SMS_DELIVERED_ACTION_${System.currentTimeMillis()}"

        val smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Check if the received action matches our unique action
                if (intent?.action == smsDeliveredAction) {
                    // Set the delivery timestamp and release the latch
                    deliveryTime.set(System.currentTimeMillis())
                    latch.countDown()
                }
            }
        }

        try {
            // Create a unique PendingIntent to be triggered on delivery
            val deliveryIntent = Intent(smsDeliveredAction)
            val deliveryPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                deliveryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE is correct for modern Android
            )

            // Register the receiver with the unique action
            ContextCompat.registerReceiver(
                context,
                smsReceiver,
                IntentFilter(smsDeliveredAction),
                ContextCompat.RECEIVER_NOT_EXPORTED // Securely register the receiver
            )

            // Start time measurement and send the SMS
            val startTime = System.currentTimeMillis()
            SmsManager.getDefault().sendTextMessage(
                phoneNumber,
                null,
                message,
                null, // This is for sent report; we don't need it for this test
                deliveryPendingIntent
            )

            // Wait for the delivery report with a timeout
            val delivered = latch.await(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)

            return if (delivered) {
                deliveryTime.get() - startTime
            } else {
                -1L // Timeout occurred because the broadcast was not received
            }

        } catch (e: Exception) {
            // Handle exceptions gracefully
            e.printStackTrace()
            return -1L
        } finally {
            try {
                // Always unregister the receiver to prevent a memory leak
                context.unregisterReceiver(smsReceiver)
            } catch (e: Exception) {
                // Ignore if the receiver was never registered, as this can happen in some error states
            }
        }
    }
}