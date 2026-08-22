package com.shinji.serena.braille

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * BrailleDisplayController
 *
 * Serena Screen Reader の点字ディスプレイ制御マネージャー。
 * Bluetooth SPP / USB 経由で点字ディスプレイと双方向通信を行い、
 * 音声読み上げと同期して点字ピンを駆動＆点字キー入力での画面操作を実現する。
 */
class BrailleDisplayController(
    private val context: Context,
    private val listener: BrailleInteractionListener
) {

    interface BrailleInteractionListener {
        fun onBrailleCommand(command: BrailleProtocolHandler.BrailleCommand, routingIndex: Int)
        fun onBrailleConnectionStateChanged(connected: Boolean, deviceName: String)
        fun onBrailleKeyInput(dots: Int)
    }

    companion object {
        private const val TAG = "BrailleController"
        // 標準 SPP (Serial Port Profile) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val protocolHandler = BrailleProtocolHandler(displayCellCount = 40)

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var isConnected = false
    private var connectedDeviceName = ""
    private var isWorkerRunning = false

    private var currentDisplayText = ""
    private var currentBrailleBuffer = ByteArray(40)

    val isBrailleConnected: Boolean
        get() = isConnected

    val currentDeviceName: String
        get() = connectedDeviceName

    /**
     * フォーカスされたノードのテキストやUI情報を点字ディスプレイに表示
     */
    fun displayNodeInfo(text: String, roleOrState: String = "", cursorPosition: Int = -1) {
        val fullText = if (roleOrState.isNotEmpty()) {
            "$text ($roleOrState)"
        } else {
            text
        }

        if (fullText == currentDisplayText && isConnected) return
        currentDisplayText = fullText

        // 日本語点字に変換
        val brailleBytes = JapaneseBrailleTranslator.textToBrailleBytes(fullText)
        val formattedBuffer = protocolHandler.formatDisplayBuffer(brailleBytes, cursorPosition)
        currentBrailleBuffer = formattedBuffer

        sendBrailleBufferToDevice(formattedBuffer)
    }

    /**
     * 生の点字セルバイト列をディスプレイへ送信
     */
    @Synchronized
    private fun sendBrailleBufferToDevice(buffer: ByteArray) {
        if (!isConnected || outputStream == null) return

        try {
            outputStream?.write(buffer)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to braille display", e)
            disconnect()
        }
    }

    /**
     * ペアリング済みの点字ディスプレイデバイスを自動探索して接続
     */
    @SuppressLint("MissingPermission")
    fun connectToPairedBrailleDevice(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!bluetoothAdapter.isEnabled) return false

        val pairedDevices = try {
            bluetoothAdapter.bondedDevices
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get bonded devices", e)
            emptySet<BluetoothDevice>()
        }

        // 点字ディスプレイらしい名前を持つデバイスを優先探索
        val brailleDevice = pairedDevices.firstOrNull { device ->
            val name = (device.name ?: "").lowercase()
            name.contains("focus") || name.contains("braille") || name.contains("orbit") ||
                    name.contains("seika") || name.contains("bmsmart") || name.contains("vario") ||
                    name.contains("alva") || name.contains("display")
        } ?: pairedDevices.firstOrNull()

        if (brailleDevice != null) {
            return connectToDevice(brailleDevice)
        }

        return false
    }

    /**
     * 特定のBluetoothデバイスに接続
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        disconnect()

        Thread {
            try {
                Log.i(TAG, "Connecting to braille display: ${device.name} (${device.address})")
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                bluetoothSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                isConnected = true
                connectedDeviceName = device.name ?: device.address

                handler.post {
                    listener.onBrailleConnectionStateChanged(true, connectedDeviceName)
                }

                // 接続完了後、現在のテキストを即時表示
                if (currentDisplayText.isNotEmpty()) {
                    displayNodeInfo(currentDisplayText)
                } else {
                    displayNodeInfo("Serena 点字接続完了")
                }

                // 受信監視ループ開始
                startReadWorker()

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed to braille display", e)
                disconnect()
                handler.post {
                    listener.onBrailleConnectionStateChanged(false, "")
                }
            }
        }.start()

        return true
    }

    /**
     * 点字ディスプレイからのキー入力受信ループ
     */
    private fun startReadWorker() {
        isWorkerRunning = true
        Thread {
            val buffer = ByteArray(64)
            while (isWorkerRunning && isConnected) {
                try {
                    val stream = inputStream ?: break
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        val packet = buffer.copyOf(bytesRead)
                        val event = protocolHandler.parseIncomingPacket(packet)
                        if (event != null) {
                            handler.post {
                                handleIncomingBrailleEvent(event)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isWorkerRunning) {
                        Log.e(TAG, "Read error from braille display", e)
                        disconnect()
                    }
                    break
                }
            }
        }.start()
    }

    private fun handleIncomingBrailleEvent(event: BrailleProtocolHandler.KeyInputEvent) {
        when (event.command) {
            BrailleProtocolHandler.BrailleCommand.BRAILLE_KEY_INPUT -> {
                listener.onBrailleKeyInput(event.rawDots)
            }
            BrailleProtocolHandler.BrailleCommand.ROUTING_CLICK -> {
                listener.onBrailleCommand(event.command, event.routingCellIndex)
            }
            else -> {
                listener.onBrailleCommand(event.command, -1)
            }
        }
    }

    /**
     * 切断処理
     */
    @Synchronized
    fun disconnect() {
        isWorkerRunning = false
        isConnected = false
        val prevName = connectedDeviceName
        connectedDeviceName = ""

        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}

        inputStream = null
        outputStream = null
        bluetoothSocket = null

        if (prevName.isNotEmpty()) {
            handler.post {
                listener.onBrailleConnectionStateChanged(false, prevName)
            }
        }
    }

    fun setCellCount(cells: Int) {
        protocolHandler.displayCellCount = cells.coerceIn(14, 80)
        if (currentDisplayText.isNotEmpty()) {
            displayNodeInfo(currentDisplayText)
        }
    }
}
