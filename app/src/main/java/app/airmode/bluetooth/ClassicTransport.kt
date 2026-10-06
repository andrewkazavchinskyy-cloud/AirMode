package app.airmode.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** No exemptions or system settings changes; one method, normal Binder permission enforcement. */
object ClassicTransport {
    fun open(device: BluetoothDevice): BluetoothSocket {
        val socket = try {
            HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "createL2capSocket", 0x1001) as? BluetoothSocket
                ?: throw IOException("Classic L2CAP unavailable")
        } catch (failure: ReflectiveOperationException) {
            throw IOException("Classic L2CAP unavailable", failure)
        } catch (failure: RuntimeException) {
            throw IOException("Classic L2CAP unavailable", failure)
        } catch (failure: LinkageError) {
            throw IOException("Classic L2CAP unavailable", failure)
        }
        if (socket.connectionType != BluetoothSocket.TYPE_L2CAP) {
            socket.close()
            throw IOException("Classic L2CAP required")
        }
        return socket
    }
}
