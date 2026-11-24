package com.grepho.cozydoubling

import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.data.fake.FakeProfileRepository
import com.grepho.cozydoubling.data.remote.ApiClient
import com.grepho.cozydoubling.data.remote.ParticipantPresenceDto
import com.grepho.cozydoubling.data.remote.RemoteFocusRoomRepository
import com.grepho.cozydoubling.data.remote.WsPresenceUpdate
import com.grepho.cozydoubling.data.remote.WsRoomMessage
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
import com.grepho.cozydoubling.features.room.RoomParticipant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class FocusRoomIntegrationTest {

    private class InJvmRoomWebSocketServer : Closeable {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        private val clients = ConcurrentHashMap<String, ClientConnection>()
        private val acceptThread: Thread

        val baseUrl: String get() = "http://127.0.0.1:${serverSocket.localPort}"

        private class ClientConnection(
            val presenceKey: String,
            val socket: Socket,
            val out: OutputStream,
            @Volatile var presence: ParticipantPresenceDto
        ) {
            fun sendTextFrame(text: String) {
                val payload = text.toByteArray(StandardCharsets.UTF_8)
                sendFrame(opcode = 0x1, payload = payload)
            }

            fun sendPongFrame(payload: ByteArray) {
                sendFrame(opcode = 0xA, payload = payload)
            }

            @Synchronized
            private fun sendFrame(opcode: Int, payload: ByteArray) {
                val frame = ByteArrayOutputStream()
                frame.write(0x80 or opcode)
                when {
                    payload.size < 126 -> frame.write(payload.size)
                    payload.size <= 65535 -> {
                        frame.write(126)
                        frame.write((payload.size ushr 8) and 0xFF)
                        frame.write(payload.size and 0xFF)
                    }
                    else -> error("Frame too large")
                }
                frame.write(payload)
                out.write(frame.toByteArray())
                out.flush()
            }
        }

        init {
            acceptThread = Thread {
                while (running.get()) {
                    try {
                        val socket = serverSocket.accept()
                        Thread { handleSocket(socket) }.apply { isDaemon = true }.start()
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        private fun handleSocket(socket: Socket) {
            var activeKey: String? = null
            try {
                val input = BufferedInputStream(socket.getInputStream())
                val out = socket.getOutputStream()

                val requestLine = readHttpLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val target = parts[1]

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] =
                            line.substring(idx + 1).trim()
                    }
                }

                if (headers["upgrade"]?.equals("websocket", ignoreCase = true) == true &&
                    target.startsWith("/ws/room")
                ) {
                    val wsKey = headers["sec-websocket-key"] ?: return
                    val acceptKey = computeWebSocketAccept(wsKey)
                    val handshake = (
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"
                        ).toByteArray(StandardCharsets.UTF_8)
                    out.write(handshake)
                    out.flush()

                    val params = parseQueryParams(target)
                    val pKey = params["presence_key"] ?: UUID.randomUUID().toString()
                    val name = params["name"] ?: "Cozy Doubler"
                    val initialPresence = ParticipantPresenceDto(
                        id = pKey,
                        name = name,
                        activeTaskText = "Focusing",
                        completedTasks = 0,
                        totalTasks = 0
                    )
                    val conn = ClientConnection(pKey, socket, out, initialPresence)
                    clients[pKey] = conn
                    activeKey = pKey

                    val others = clients.values
                        .filter { it.presenceKey != pKey }
                        .map { it.presence }
                    conn.sendTextFrame(
                        json.encodeToString(
                            WsRoomMessage(type = "presence_state", participants = others)
                        )
                    )

                    val joinMsg = json.encodeToString(
                        WsRoomMessage(type = "presence_update", presence = initialPresence)
                    )
                    broadcastExcept(pKey, joinMsg)

                    while (running.get() && !socket.isClosed) {
                        val (opcode, payload) = readClientFrame(input) ?: break
                        when (opcode) {
                            0x1 -> { // TEXT
                                val text = String(payload, StandardCharsets.UTF_8)
                                val update = json.decodeFromString<WsPresenceUpdate>(text)
                                val canonical = update.presence.copy(id = pKey)
                                conn.presence = canonical
                                broadcastExcept(
                                    pKey,
                                    json.encodeToString(
                                        WsRoomMessage(type = "presence_update", presence = canonical)
                                    )
                                )
                            }
                            0x8 -> { // CLOSE
                                break
                            }
                            0x9 -> { // PING
                                conn.sendPongFrame(payload)
                            }
                        }
                    }
                } else {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength > 0) {
                        readExact(input, contentLength)
                    }
                    val responseBody = when {
                        method == "POST" && target == "/api/sessions/start" ->
                            """{"session_id":"${UUID.randomUUID()}"}"""
                        method == "POST" && target.startsWith("/api/sessions/") && target.endsWith("/finish") ->
                            """{}"""
                        else -> """{}"""
                    }
                    val respBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                    val resp = (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(StandardCharsets.UTF_8)
                    out.write(resp)
                    out.write(respBytes)
                    out.flush()
                }
            } catch (_: Exception) {
                // Socket closed
            } finally {
                if (activeKey != null) {
                    val removed = clients.remove(activeKey)
                    if (removed != null) {
                        val leaveMsg = json.encodeToString(
                            WsRoomMessage(type = "presence_leave", presenceId = activeKey)
                        )
                        broadcastExcept(activeKey, leaveMsg)
                    }
                }
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }

        private fun broadcastExcept(senderKey: String, text: String) {
            clients.values.filter { it.presenceKey != senderKey }.forEach { peer ->
                try {
                    peer.sendTextFrame(text)
                } catch (_: Exception) {
                }
            }
        }

        private fun readHttpLine(input: InputStream): String? {
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
                if (b == '\n'.code) {
                    val raw = buf.toString("UTF-8")
                    return raw.removeSuffix("\r")
                }
                buf.write(b)
            }
        }

        private fun readClientFrame(input: InputStream): Pair<Int, ByteArray>? {
            val b0 = input.read()
            if (b0 == -1) return null
            val b1 = input.read()
            if (b1 == -1) return null

            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                val hi = input.read()
                val lo = input.read()
                if (hi == -1 || lo == -1) return null
                len = ((hi shl 8) or lo).toLong()
            } else if (len == 127L) {
                len = 0L
                repeat(8) {
                    val v = input.read()
                    if (v == -1) return null
                    len = (len shl 8) or v.toLong()
                }
            }
            val mask = if (masked) readExact(input, 4) else null
            val payload = readExact(input, len.toInt())
            if (mask != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }
            return opcode to payload
        }

        private fun readExact(input: InputStream, count: Int): ByteArray {
            val buf = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(buf, offset, count - offset)
                if (read == -1) throw EOFException("Unexpected EOF")
                offset += read
            }
            return buf
        }

        private fun parseQueryParams(target: String): Map<String, String> {
            val qIdx = target.indexOf('?')
            if (qIdx == -1) return emptyMap()
            return target.substring(qIdx + 1)
                .split("&")
                .mapNotNull { pair ->
                    val eq = pair.indexOf('=')
                    if (eq <= 0) null
                    else {
                        val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                        val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                        k to v
                    }
                }
                .toMap()
        }

        private fun computeWebSocketAccept(key: String): String {
            val magic = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            val hash = MessageDigest.getInstance("SHA-1").digest(magic.toByteArray(StandardCharsets.UTF_8))
            return Base64.getEncoder().encodeToString(hash)
        }

        override fun close() {
            running.set(false)
            clients.values.forEach {
                try {
                    it.socket.close()
                } catch (_: Exception) {
                }
            }
            clients.clear()
            try {
                serverSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun projectOtherParticipants(
        presences: List<ParticipantPresence>,
        myUserId: String,
        blockedIds: Set<String> = emptySet()
    ): List<RoomParticipant> {
        return presences
            .filter { participant ->
                val profileId = participant.id.split(":").first()
                profileId != myUserId && profileId !in blockedIds
            }
            .groupBy { it.id.split(":").first() }
            .map { (profileId, list) ->
                val latest = list.last()
                RoomParticipant(
                    id = profileId,
                    name = latest.name,
                    activeTaskText = latest.displayTaskText,
                    completedTasks = latest.completedTasks,
                    totalTasks = latest.totalTasks
                )
            }
    }

    @Test
    fun twoRealOkHttpClientsSeeEachOtherAndExchangeLiveTaskUpdates() = runBlocking {
        InJvmRoomWebSocketServer().use { server ->
            val eminId = "user_emin_01"
            val osmanId = "user_osman_02"

            val eminProfileRepo = FakeProfileRepository(
                authRepository = null,
                initialProfile = Profile(id = eminId, displayName = "emin", playerTag = "EMIN1111")
            )
            val osmanProfileRepo = FakeProfileRepository(
                authRepository = null,
                initialProfile = Profile(id = osmanId, displayName = "osman", playerTag = "OSMN2222")
            )

            val eminClient = ApiClient(baseUrl = server.baseUrl).apply { authToken = "token_emin" }
            val osmanClient = ApiClient(baseUrl = server.baseUrl).apply { authToken = "token_osman" }

            val eminRoomRepo = RemoteFocusRoomRepository(eminClient, eminProfileRepo)
            val osmanRoomRepo = RemoteFocusRoomRepository(osmanClient, osmanProfileRepo)

            val sessEmin = eminRoomRepo.startSession()
            val sessOsman = osmanRoomRepo.startSession()
            assertNotNull(sessEmin)
            assertNotNull(sessOsman)

            val eminPresenceKey = "$eminId:$sessEmin"
            val osmanPresenceKey = "$osmanId:$sessOsman"

            val eminFlow = eminRoomRepo.joinRoom(eminPresenceKey)
            val osmanFlow = osmanRoomRepo.joinRoom(osmanPresenceKey)

            // 1. Emin joins first and connects
            eminRoomRepo.subscribe()
            withTimeout(5000) {
                eminRoomRepo.getChannelStatus().first { it == RoomConnectionStatus.CONNECTED }
            }
            assertEquals(emptyList<ParticipantPresence>(), eminFlow.first())

            // 2. Osman joins while Emin is in the room
            osmanRoomRepo.subscribe()
            withTimeout(5000) {
                osmanRoomRepo.getChannelStatus().first { it == RoomConnectionStatus.CONNECTED }
            }

            // Both clients must see each other via real OkHttp WebSocket messages
            val eminSeesOsman = withTimeout(5000) {
                eminFlow
                    .map { projectOtherParticipants(it, eminId) }
                    .first { list -> list.any { it.id == osmanId } }
            }
            assertEquals(1, eminSeesOsman.size)
            assertEquals("osman", eminSeesOsman[0].name)

            val osmanSeesEmin = withTimeout(5000) {
                osmanFlow
                    .map { projectOtherParticipants(it, osmanId) }
                    .first { list -> list.any { it.id == eminId } }
            }
            assertEquals(1, osmanSeesEmin.size)
            assertEquals("emin", osmanSeesEmin[0].name)

            // 3. Osman updates active task -> Emin's repository stream reflects it in real time
            osmanRoomRepo.updatePresence(
                ParticipantPresence(
                    id = osmanPresenceKey,
                    name = "osman",
                    activeTaskText = "Fixing clean architecture",
                    completedTasks = 1,
                    totalTasks = 2
                )
            )
            val updatedForEmin = withTimeout(5000) {
                eminFlow
                    .map { projectOtherParticipants(it, eminId) }
                    .first { list -> list.any { it.activeTaskText == "Fixing clean architecture" } }
            }
            assertEquals(1, updatedForEmin[0].completedTasks)
            assertEquals(2, updatedForEmin[0].totalTasks)

            // 4. Osman leaves -> Emin sees empty room again
            osmanRoomRepo.leaveRoom()
            val afterLeaveForEmin = withTimeout(5000) {
                eminFlow
                    .map { projectOtherParticipants(it, eminId) }
                    .first { it.isEmpty() }
            }
            assertTrue(afterLeaveForEmin.isEmpty())

            eminRoomRepo.leaveRoom()
        }
    }
}
