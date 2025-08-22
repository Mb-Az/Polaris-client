

data class TestResult(
    val ping: Long? = null,
    val web: Long? = null,
    val dns: Long? = null,
    val throughput: Long? = null,
    val sms: Long? = null
)