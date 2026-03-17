package com.example.divyanyanreader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FileTransferService : Service() {

    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val running = AtomicBoolean(false)
    private val ioExecutor = Executors.newCachedThreadPool()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        running.set(true)
        ioExecutor.execute { startUdpDiscoveryLoop() }
        ioExecutor.execute { startTcpReceiverLoop() }
        broadcastStatus("Receiver started. Waiting for Raspberry Pi...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FileTransferService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }

        val stopPendingIntent = android.app.PendingIntent.getService(
            this,
            0,
            stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("File Receiver Active")
            .setContentText("Waiting for images from Raspberry Pi")
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfer Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startUdpDiscoveryLoop() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("divyanyan_udp_lock")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        try {
            udpSocket = DatagramSocket(UDP_PORT)
            broadcastStatus("UDP discovery listening on $UDP_PORT")
            val buffer = ByteArray(1024)

            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet)
                val message = String(packet.data, 0, packet.length)

                if (message == DISCOVERY_REQUEST) {
                    val responseBytes = DISCOVERY_RESPONSE.toByteArray()
                    val responsePacket = DatagramPacket(
                        responseBytes,
                        responseBytes.size,
                        packet.address,
                        packet.port
                    )
                    udpSocket?.send(responsePacket)
                    broadcastStatus("Discovery request received from ${packet.address.hostAddress}")
                }
            }
        } catch (_: SocketException) {
            // Expected when closing socket on service stop.
        } catch (error: Exception) {
            Log.e(TAG, "UDP discovery error", error)
            broadcastStatus("UDP error: ${error.message}")
        } finally {
            udpSocket?.close()
            udpSocket = null
            multicastLock?.release()
            multicastLock = null
        }
    }

    private fun startTcpReceiverLoop() {
        try {
            tcpServerSocket = ServerSocket(TCP_PORT)
            broadcastStatus("TCP receiver listening on $TCP_PORT")

            while (running.get()) {
                val socket = tcpServerSocket?.accept() ?: break
                ioExecutor.execute { handleIncomingFile(socket) }
            }
        } catch (_: SocketException) {
            // Expected when closing server socket on service stop.
        } catch (error: Exception) {
            Log.e(TAG, "TCP receiver error", error)
            broadcastStatus("TCP error: ${error.message}")
        } finally {
            tcpServerSocket?.close()
            tcpServerSocket = null
        }
    }

    private fun handleIncomingFile(socket: Socket) {
        socket.use { client ->
            try {
                val input = client.getInputStream()
                val header = readLine(input) ?: return
                val parts = header.split("|")
                if (parts.size < 2) {
                    broadcastStatus("Invalid header received")
                    return
                }

                val fileName = parts[0].ifBlank { "received_${System.currentTimeMillis()}.jpg" }
                val fileSize = parts[1].toLongOrNull() ?: run {
                    broadcastStatus("Invalid file size in header")
                    return
                }

                client.getOutputStream().write("OK".toByteArray())

                val receivedFile = saveIncomingBytes(input, fileName, fileSize)
                if (receivedFile != null) {
                    broadcastStatus("Received ${receivedFile.name} (${receivedFile.length()} bytes)")
                    launchProcessImage(receivedFile)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Error receiving file", error)
                broadcastStatus("File receive error: ${error.message}")
            }
        }
    }

    private fun saveIncomingBytes(input: InputStream, fileName: String, fileSize: Long): File? {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(cacheDir, "rx_${System.currentTimeMillis()}_$safeName")

        FileOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalRead = 0L
            while (totalRead < fileSize) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalRead).toInt())
                if (read <= 0) break
                output.write(buffer, 0, read)
                totalRead += read
            }

            if (totalRead != fileSize) {
                target.delete()
                broadcastStatus("Incomplete file received ($totalRead/$fileSize bytes)")
                return null
            }
        }

        return target
    }

    private fun launchProcessImage(imageFile: File) {
        val intent = ProcessImageActivity.createIntent(applicationContext, imageFile.absolutePath).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun readLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream(128)
        while (true) {
            val value = input.read()
            if (value == -1) break
            if (value == '\n'.code) break
            if (value != '\r'.code) {
                bytes.write(value)
            }
        }

        val lineBytes = bytes.toByteArray()
        if (lineBytes.isEmpty()) return null
        return lineBytes.toString(Charsets.UTF_8)
    }

    private fun broadcastStatus(message: String) {
        sendBroadcast(Intent(ACTION_RECEIVER_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, message)
        })
    }

    override fun onDestroy() {
        running.set(false)
        udpSocket?.close()
        tcpServerSocket?.close()
        multicastLock?.release()
        multicastLock = null
        ioExecutor.shutdownNow()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
        broadcastStatus("Receiver stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FileTransferService"
        private const val CHANNEL_ID = "file_transfer_channel"
        private const val NOTIFICATION_ID = 101
        private const val UDP_PORT = 50000
        private const val TCP_PORT = 50001
        private const val DISCOVERY_REQUEST = "DISCOVER_ANDROID_DEVICE"
        private const val DISCOVERY_RESPONSE = "ANDROID_HERE"

        const val ACTION_STOP_SERVICE = "com.example.divyanyanreader.action.STOP_TRANSFER_SERVICE"
        const val ACTION_RECEIVER_STATUS = "com.example.divyanyanreader.action.RECEIVER_STATUS"
        const val EXTRA_STATUS = "extra_status"
    }
}