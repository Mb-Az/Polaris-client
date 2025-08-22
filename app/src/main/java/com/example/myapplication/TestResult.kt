

data class TestResult(
    val ping: Long? = null,
    val web: Long? = null,
    val dns: Long? = null,
    val throughput: Long? = null,
    val sms: Long? = null
){
    fun hasFailedTests(): Boolean {
        // Check if any of the test results are -1L
        return (ping != null && ping == -1L) ||
                (web != null && web == -1L) ||
                (dns != null && dns == -1L) ||
                (throughput != null && throughput == -1L) ||
                (sms != null && sms == -1L)
    }
}