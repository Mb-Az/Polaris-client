import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresPermission

/**
 * A class to collect and manage cellular network information.
 * It requires ACCESS_FINE_LOCATION and READ_PHONE_STATE permissions.
 */
public class CellInfoCollector(private val context: Context) {

    private val telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getCellInfo(): CellInfo {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return CellInfo()
        }

        val cellInfoList: List<android.telephony.CellInfo>? = try {
            telephonyManager.allCellInfo
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        }

        val registeredCellInfo = cellInfoList
            ?.filter { it.isRegistered }
            ?.firstOrNull()

        return registeredCellInfo?.let {
            CellInfo(
                signalLevel = getSignalLevel(it),
                carrier = getCarrier(it),
                technology = getTechnology(it),
                tac = getTac(it),
                plmnId = getPlmnId(it),
                arfcn = getArfcn(it),
                rsrq = getRsrq(it),
                rsrp = getRsrp(it),
                rscp = getRscp(it),
                ecNo = getEcNo(it),
                rxLev = getRxLev(it)
            )
        } ?: CellInfo()
    }


    private fun getSignalLevel(cell: android.telephony.CellInfo): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return cell.cellSignalStrength.level
        else {
            @Suppress("DEPRECATION") // Deprecated APIs are used for older versions
            return when (cell) {
                is CellInfoGsm -> cell.cellSignalStrength.level
                is CellInfoCdma -> cell.cellSignalStrength.level
                is CellInfoLte -> cell.cellSignalStrength.level
                is CellInfoWcdma -> cell.cellSignalStrength.level
                else -> null
            }
        }
    }

    private fun getCarrier(cell: android.telephony.CellInfo): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            telephonyManager.simCarrierIdName?.toString()
        } else {
            telephonyManager.networkOperatorName
        }
    }

    private fun getTechnology(cell: android.telephony.CellInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return when (cell) {
                is CellInfoLte -> "LTE"
                is CellInfoGsm -> "GSM"
                is CellInfoWcdma -> "WCDMA"
                is CellInfoCdma -> "CDMA"
                is CellInfoNr -> "NR (5G)"
                else -> null
        }
        else
            return when (cell) {
                is CellInfoLte -> "LTE"
                is CellInfoGsm -> "GSM"
                is CellInfoWcdma -> "WCDMA"
                is CellInfoCdma -> "CDMA"
                else -> null
            }
    }

    private fun getTac(cell: android.telephony.CellInfo): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return when (cell) {
                is CellInfoLte -> cell.cellIdentity.tac
                is CellInfoWcdma -> cell.cellIdentity.lac
                is CellInfoCdma -> cell.cellIdentity.basestationId
                is CellInfoNr -> (cell.cellIdentity as? CellIdentityNr)?.tac
                else -> null
        }
        else
            return when (cell) {
                is CellInfoLte -> cell.cellIdentity.tac
                is CellInfoWcdma -> cell.cellIdentity.lac
                is CellInfoCdma -> cell.cellIdentity.basestationId
                else -> null
            }

    }

    private fun getPlmnId(cell: android.telephony.CellInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return when (cell) {
                is CellInfoLte -> getPlmnFromIdentity(cell.cellIdentity.mccString, cell.cellIdentity.mncString)
                is CellInfoGsm -> getPlmnFromIdentity(cell.cellIdentity.mccString, cell.cellIdentity.mncString)
                is CellInfoWcdma -> getPlmnFromIdentity(cell.cellIdentity.mccString, cell.cellIdentity.mncString)
                is CellInfoNr -> getPlmnFromIdentity((cell.cellIdentity as? CellIdentityNr)?.mccString, (cell.cellIdentity as? CellIdentityNr)?.mncString)
                else -> null
            }
        else
            return when (cell) {
                is CellInfoLte -> getPlmnFromIdentity(cell.cellIdentity.mccString, cell.cellIdentity.mncString)
                is CellInfoGsm -> getPlmnFromIdentity(cell.cellIdentity.mccString, cell.cellIdentity.mncString)
                is CellInfoWcdma -> getPlmnFromIdentity(cell.cellIdentity.mccString, cell.cellIdentity.mncString)
                else -> null
            }

    }

    private fun getPlmnFromIdentity(mcc: String?, mnc: String?): String? {
        return if (mcc != null && mnc != null) "$mcc$mnc" else null
    }

    private fun getArfcn(cell: android.telephony.CellInfo): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return when (cell) {
                is CellInfoLte -> cell.cellIdentity.earfcn
                is CellInfoGsm -> cell.cellIdentity.arfcn
                is CellInfoWcdma -> cell.cellIdentity.uarfcn
                is CellInfoNr -> (cell.cellIdentity as? CellIdentityNr)?.nrarfcn
                else -> null
            }
        else
            return when (cell) {
                is CellInfoLte -> cell.cellIdentity.earfcn
                is CellInfoGsm -> cell.cellIdentity.arfcn
                is CellInfoWcdma -> cell.cellIdentity.uarfcn
                else -> null
            }
    }

    private fun getRsrq(cell: android.telephony.CellInfo): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return when (cell) {
                is CellInfoLte ->  cell.cellSignalStrength.rsrq
                is CellInfoNr -> (cell.cellSignalStrength as CellSignalStrengthNr).ssRsrq
                else -> null
            }
        else
            return when (cell) {
                is CellInfoLte ->  cell.cellSignalStrength.rsrq
                else -> null
            }
    }

    private fun getRsrp(cell: android.telephony.CellInfo): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return when (cell) {
                is CellInfoLte -> cell.cellSignalStrength.rsrp
                is CellInfoNr -> (cell.cellSignalStrength as CellSignalStrengthNr).ssRsrp
                else -> null
            }
        else
            return when (cell) {
                is CellInfoLte -> cell.cellSignalStrength.rsrp
                else -> null
            }
    }

    private fun getRscp(cell: android.telephony.CellInfo): Int? {
        // RSCP only meaningful for WCDMA
        if (cell !is CellInfoWcdma) return null

        val strength = cell.cellSignalStrength as? CellSignalStrengthWcdma ?: return null

        // getDbm() returns RSCP in dBm (-120..-24) or CellInfo.UNAVAILABLE
        val dbm = strength.getDbm()
        return if (dbm == android.telephony.CellInfo.UNAVAILABLE) null else dbm
    }

    private fun getEcNo(cell: android.telephony.CellInfo): Int? {
        // Ec/N0 is for WCDMA and CDMA.
        return when (cell) {
            is CellInfoWcdma -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cell.cellSignalStrength.ecNo else null
            else -> null
        }
    }

    private fun getRxLev(cell: android.telephony.CellInfo): Int? {
        return when (cell) {
            is CellInfoGsm -> cell.cellSignalStrength.dbm
            is CellInfoCdma -> cell.cellSignalStrength.cdmaDbm
            else -> null
        }
    }
}