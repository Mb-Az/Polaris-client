

data class CellInfo(
    val signalLevel: Int? = null,
    val carrier: String? = null,
    val technology: String? = null,
    val tac: Int? = null,
    val plmnId: String? = null,
    val arfcn: Int? = null,
    val rsrq: Int? = null,
    val rsrp: Int? = null,
    val rscp: Int? = null,
    val ecNo: Int? = null,
    val rxLev: Int? = null
)