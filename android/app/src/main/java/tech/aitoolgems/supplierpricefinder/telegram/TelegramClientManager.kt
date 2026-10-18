package tech.aitoolgems.supplierpricefinder.telegram

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import tech.aitoolgems.supplierpricefinder.data.local.SecureStorage
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class TelegramAuthState {
    DISCONNECTED,
    CONNECTING,
    WAIT_PHONE_NUMBER,
    WAIT_CODE,
    WAIT_PASSWORD,
    AUTHENTICATED,
    ERROR
}

class TelegramClientManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TelegramClientManager"

        @Volatile
        private var instance: TelegramClientManager? = null

        fun getInstance(context: Context): TelegramClientManager {
            return instance ?: synchronized(this) {
                instance ?: TelegramClientManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var client: Client? = null
    private val isInitializing = AtomicBoolean(false)

    var currentAuthState: TelegramAuthState = TelegramAuthState.DISCONNECTED
        private set

    var statusMessage: String = "Not connected"
        private set

    var authenticatedUserName: String? = null
        private set

    var authenticatedUserId: Long = 0
        private set

    private var targetApiId: Int = 0
    private var targetApiHash: String = ""
    private var targetPhoneNumber: String = ""

    var onStateChangedListener: ((TelegramAuthState, String) -> Unit)? = null
    var onConnectionStateListener: ((String) -> Unit)? = null

    private val botMessageListeners = ConcurrentHashMap<Long, (TdApi.Message) -> Unit>()
    private val botUpdateListeners = ConcurrentHashMap<Long, (TdApi.Object) -> Unit>()

    private fun notifyState(state: TelegramAuthState, message: String) {
        currentAuthState = state
        statusMessage = message
        Log.i(TAG, "[$state] $message")
        mainHandler.post {
            onStateChangedListener?.invoke(state, message)
        }
    }

    private fun notifyConnection(info: String) {
        Log.i(TAG, "[Connection] $info")
        mainHandler.post {
            onConnectionStateListener?.invoke(info)
        }
    }

    /**
     * Start the genuine MTProto authentication flow.
     * This connects directly to Telegram Data Centers and dispatches
     * the official 5-digit verification code to the user's Telegram apps.
     */
    fun startAuthentication(
        apiId: Int,
        apiHash: String,
        phoneNumber: String
    ) {
        targetApiId = apiId
        targetApiHash = apiHash
        targetPhoneNumber = if (!phoneNumber.startsWith("+")) "+$phoneNumber" else phoneNumber

        notifyState(TelegramAuthState.CONNECTING, "Connecting to Telegram MTProto Data Centers with API ID $apiId...")

        try {
            if (client == null) {
                initClient()
            } else {
                // If already initialized and waiting for phone number, submit directly
                submitPhoneNumber(targetPhoneNumber)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize TDLib client", e)
            notifyState(
                TelegramAuthState.ERROR,
                "Telegram MTProto Engine error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun initClient() {
        if (isInitializing.getAndSet(true)) return

        try {
            client = Client.create(
                { update -> handleUpdate(update) },
                { error ->
                    Log.e(TAG, "TDLib update exception", error)
                },
                { error ->
                    Log.e(TAG, "TDLib default exception", error)
                }
            )
            isInitializing.set(false)
        } catch (t: Throwable) {
            isInitializing.set(false)
            Log.e(TAG, "Native library load failed", t)
            notifyState(TelegramAuthState.ERROR, "Native TDLib error: ${t.message}")
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthorizationState(update.authorizationState)
            }
            is TdApi.UpdateConnectionState -> {
                handleConnectionState(update.state)
            }
            is TdApi.UpdateNewMessage -> {
                val msg = update.message
                botMessageListeners[msg.chatId]?.invoke(msg)
                botUpdateListeners[msg.chatId]?.invoke(update)
            }
            is TdApi.UpdateMessageContent -> {
                botUpdateListeners[update.chatId]?.invoke(update)
            }
            is TdApi.UpdateMessageEdited -> {
                botUpdateListeners[update.chatId]?.invoke(update)
            }
        }
    }

    private fun handleConnectionState(state: TdApi.ConnectionState) {
        when (state) {
            is TdApi.ConnectionStateWaitingForNetwork -> {
                notifyConnection("Waiting for network connection... (If in Pakistan, please turn on VPN)")
            }
            is TdApi.ConnectionStateConnectingToProxy -> {
                notifyConnection("Connecting to Telegram proxy...")
            }
            is TdApi.ConnectionStateConnecting -> {
                notifyConnection("Connecting to Telegram MTProto DC servers...")
            }
            is TdApi.ConnectionStateUpdating -> {
                notifyConnection("Updating Telegram dialogs...")
            }
            is TdApi.ConnectionStateReady -> {
                notifyConnection("Connected to Telegram Data Center!")
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                sendTdlibParameters()
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                notifyState(TelegramAuthState.WAIT_PHONE_NUMBER, "Ready for phone number.")
                if (targetPhoneNumber.isNotEmpty()) {
                    submitPhoneNumber(targetPhoneNumber)
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val codeInfo = state.codeInfo
                val dest = when (codeInfo?.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> "official Telegram chat"
                    is TdApi.AuthenticationCodeTypeSms -> "SMS"
                    is TdApi.AuthenticationCodeTypeCall -> "Phone Call"
                    else -> "Telegram chat"
                }
                notifyState(
                    TelegramAuthState.WAIT_CODE,
                    "✓ Verification code dispatched by Telegram! Please open your $dest on your laptop or mobile to view your 5-digit code."
                )
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                notifyState(
                    TelegramAuthState.WAIT_PASSWORD,
                    "Your Telegram account has 2-Step Verification (2FA) enabled. Please enter your cloud password."
                )
            }
            is TdApi.AuthorizationStateReady -> {
                // Fetch logged-in user info
                client?.send(TdApi.GetMe()) { result ->
                    if (result is TdApi.User) {
                        authenticatedUserId = result.id
                        val displayName = "${result.firstName} ${result.lastName}".trim()
                        authenticatedUserName = if (displayName.isNotEmpty()) displayName else result.phoneNumber
                        notifyState(
                            TelegramAuthState.AUTHENTICATED,
                            "✓ Successfully connected as $authenticatedUserName! Ready to inspect supplier bots."
                        )
                    } else {
                        notifyState(
                            TelegramAuthState.AUTHENTICATED,
                            "✓ Successfully connected to Telegram MTProto!"
                        )
                    }
                }
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                notifyState(TelegramAuthState.DISCONNECTED, "Logging out...")
            }
            is TdApi.AuthorizationStateClosing -> {
                notifyState(TelegramAuthState.DISCONNECTED, "Closing Telegram session...")
            }
            is TdApi.AuthorizationStateClosed -> {
                notifyState(TelegramAuthState.DISCONNECTED, "Telegram session closed.")
                client = null
            }
            else -> {
                Log.d(TAG, "Unhandled auth state: ${state.javaClass.simpleName}")
            }
        }
    }

    private fun sendTdlibParameters() {
        val tdlibDb = File(context.filesDir, "tdlib_data").apply { mkdirs() }
        val tdlibFiles = File(context.filesDir, "tdlib_files").apply { mkdirs() }

        val params = TdApi.SetTdlibParameters(
            false, // useTestDc
            tdlibDb.absolutePath, // databaseDirectory
            tdlibFiles.absolutePath, // filesDirectory
            byteArrayOf(), // databaseEncryptionKey
            true, // useFileDatabase
            true, // useChatInfoDatabase
            true, // useMessageDatabase
            false, // useSecretChats
            targetApiId, // apiId
            targetApiHash, // apiHash
            "en", // systemLanguageCode
            "Android " + android.os.Build.MODEL, // deviceModel
            android.os.Build.VERSION.RELEASE, // systemVersion
            "1.0.4" // applicationVersion
        )

        client?.send(params) { result ->
            if (result is TdApi.Error) {
                notifyState(TelegramAuthState.ERROR, "Parameters rejected: ${result.message} (Code: ${result.code})")
            }
        }
    }

    private fun submitPhoneNumber(phone: String) {
        notifyState(TelegramAuthState.CONNECTING, "Requesting official login code from Telegram for $phone...")
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result is TdApi.Error) {
                val errorMsg = when (result.code) {
                    400 -> "Invalid phone number ($phone) or API credentials. Check format (e.g. +81... or +92...)."
                    420 -> "Flood wait: Telegram rate limit. Please wait a few minutes before retrying."
                    else -> "Telegram error [${result.code}]: ${result.message}"
                }
                notifyState(TelegramAuthState.ERROR, errorMsg)
            }
        }
    }

    /**
     * Submit the 5-digit verification code received inside the official Telegram app.
     */
    fun submitVerificationCode(code: String, onResult: (Boolean, String) -> Unit) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) {
            onResult(false, "Please enter the 5-digit code.")
            return
        }

        client?.send(TdApi.CheckAuthenticationCode(cleanCode)) { result ->
            mainHandler.post {
                when (result) {
                    is TdApi.Ok -> {
                        onResult(true, "Code verified successfully!")
                    }
                    is TdApi.Error -> {
                        val msg = when (result.code) {
                            400 -> "Invalid code. Please re-check the 5-digit code in your Telegram app."
                            401 -> "Code expired. Please request a new code."
                            else -> result.message
                        }
                        onResult(false, msg)
                    }
                    else -> {
                        onResult(false, "Unexpected response from Telegram.")
                    }
                }
            }
        }
    }

    /**
     * Submit 2FA Cloud Password if the account has two-step verification enabled.
     */
    fun submitPassword(password: String, onResult: (Boolean, String) -> Unit) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            mainHandler.post {
                when (result) {
                    is TdApi.Ok -> onResult(true, "Password verified!")
                    is TdApi.Error -> onResult(false, "Incorrect 2FA password: ${result.message}")
                    else -> onResult(false, "Unexpected response.")
                }
            }
        }
    }

    /**
     * Log out and close the TDLib session cleanly.
     */
    fun logOut(onResult: (Boolean) -> Unit) {
        client?.send(TdApi.LogOut()) { result ->
            mainHandler.post {
                authenticatedUserName = null
                authenticatedUserId = 0
                notifyState(TelegramAuthState.DISCONNECTED, "Logged out successfully.")
                onResult(result is TdApi.Ok)
            }
        }
    }

    /**
     * Safely inspect a supplier bot by sending /start or /open and reading inline catalog.
     */
    suspend fun inspectSupplierBot(
        botHandle: String,
        targetQuery: String,
        onProgress: (String) -> Unit
    ): List<PriceObservation> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PriceObservation>()
        val currentClient = client
        if (currentClient == null || currentAuthState != TelegramAuthState.AUTHENTICATED) {
            onProgress("Telegram client not authenticated. Please connect your account first.")
            return@withContext results
        }

        val cleanHandle = botHandle.trim().removePrefix("@")
        onProgress("Resolving bot @$cleanHandle via MTProto...")

        // Search for public chat
        val chatResult = suspendAsync<TdApi.Object> { cb ->
            currentClient.send(TdApi.SearchPublicChat(cleanHandle), cb)
        }

        if (chatResult !is TdApi.Chat) {
            val err = if (chatResult is TdApi.Error) chatResult.message else "Bot not found"
            onProgress("Could not open @$cleanHandle: $err")
            return@withContext results
        }

        val chatId = chatResult.id
        onProgress("Connected to ${chatResult.title}. Opening bot menu...")

        // Send /start command
        val messageContent = TdApi.InputMessageText().apply {
            text = TdApi.FormattedText("/start", emptyArray())
        }
        val sendReq = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = messageContent
        }

        suspendAsync<TdApi.Object> { cb ->
            currentClient.send(sendReq, cb)
        }

        // Wait brief interval for bot response
        kotlinx.coroutines.delay(2000)

        // Get chat history messages
        val histResult = suspendAsync<TdApi.Object> { cb ->
            currentClient.send(TdApi.GetChatHistory(chatId, 0, 0, 5, false), cb)
        }

        if (histResult is TdApi.Messages) {
            for (msg in histResult.messages) {
                val text = when (val c = msg.content) {
                    is TdApi.MessageText -> c.text.text
                    else -> ""
                }

                if (text.isNotEmpty()) {
                    // Extract prices from bot response
                    val lines = text.split("\n")
                    for (line in lines) {
                        val (amount, currency) = PriceExtractor.extractPrice(line)
                        if (amount != null && currency != null) {
                            val lowerLine = line.lowercase()
                            val lowerQuery = targetQuery.lowercase()
                            if (targetQuery.isEmpty() || lowerLine.contains(lowerQuery)) {
                                val pkrAmount = when (currency.uppercase()) {
                                    "PKR" -> amount * 1.0
                                    "USDT" -> amount * 282.5
                                    "USD" -> amount * 280.0
                                    else -> amount * 280.0
                                }
                                results.add(
                                    PriceObservation(
                                        id = 0,
                                        supplierId = chatId,
                                        supplierName = chatResult.title,
                                        supplierHandle = "@$cleanHandle",
                                        productName = if (targetQuery.isNotEmpty()) targetQuery else line.take(30),
                                        planName = line.take(50),
                                        duration = "1 Month",
                                        accessType = AccessType.PRIVATE,
                                        matchingKey = "${cleanHandle}_${line.take(20).filter { it.isLetterOrDigit() }}",
                                        amount = amount,
                                        currency = currency,
                                        amountPkr = pkrAmount,
                                        warranty = "Bot Catalog Listed",
                                        stockStatus = "Available",
                                        verificationStatus = VerificationStatus.VERIFIED,
                                        riskLevel = RiskLevel.LOW,
                                        captureMethod = "AUTHENTICATED_TDLIB_BOT_SCANNER",
                                        originalRawText = line,
                                        sourceUrl = "https://t.me/$cleanHandle",
                                        observedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        onProgress("Bot inspection complete. Extracted ${results.size} offer(s).")
        results
    }

    fun registerBotUpdateListener(chatId: Long, listener: (TdApi.Object) -> Unit) {
        botUpdateListeners[chatId] = listener
    }

    fun unregisterBotUpdateListener(chatId: Long) {
        botUpdateListeners.remove(chatId)
    }

    suspend fun resolveBotChat(handle: String): TdApi.Chat? {
        val currentClient = client ?: return null
        val clean = handle.trim().removePrefix("@")
        val res = suspendAsync<TdApi.Object> { cb ->
            currentClient.send(TdApi.SearchPublicChat(clean), cb)
        }
        return if (res is TdApi.Chat) res else null
    }

    suspend fun sendBotTextMessage(chatId: Long, text: String): TdApi.Message? {
        val currentClient = client ?: return null
        val content = TdApi.InputMessageText().apply {
            this.text = TdApi.FormattedText(text, emptyArray())
        }
        val sendReq = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = content
        }
        val res = suspendAsync<TdApi.Object> { cb ->
            currentClient.send(sendReq, cb)
        }
        return if (res is TdApi.Message) res else null
    }

    suspend fun sendBotCallbackQuery(chatId: Long, messageId: Long, data: String): TdApi.Object? {
        val currentClient = client ?: return null
        val payload = TdApi.CallbackQueryPayloadData(data.toByteArray(Charsets.UTF_8))
        val req = TdApi.GetCallbackQueryAnswer(chatId, messageId, payload)
        return suspendAsync<TdApi.Object> { cb ->
            currentClient.send(req, cb)
        }
    }

    suspend fun joinChatChannel(targetHandleOrUrl: String): Boolean {
        val currentClient = client ?: return false
        val clean = targetHandleOrUrl.trim()
        val invitePrefix = "t.me/+"
        val joinPrefix = "t.me/joinchat/"

        return if (clean.contains(invitePrefix) || clean.contains(joinPrefix)) {
            val link = clean.substringAfter("t.me/")
            val res = suspendAsync<TdApi.Object> { cb ->
                currentClient.send(TdApi.JoinChatByInviteLink(link), cb)
            }
            res is TdApi.Chat || res is TdApi.Ok
        } else {
            val handle = clean.substringAfter("t.me/").removePrefix("@")
            val chat = resolveBotChat(handle) ?: return false
            val res = suspendAsync<TdApi.Object> { cb ->
                currentClient.send(TdApi.JoinChat(chat.id), cb)
            }
            res is TdApi.Ok
        }
    }

    suspend fun fetchMessageSync(chatId: Long, messageId: Long): TdApi.Message? {
        val currentClient = client ?: return null
        val res = suspendAsync<TdApi.Object> { cb ->
            currentClient.send(TdApi.GetMessage(chatId, messageId), cb)
        }
        return if (res is TdApi.Message) res else null
    }

    private suspend inline fun <reified T : TdApi.Object> suspendAsync(
        crossinline block: (Client.ResultHandler) -> Unit
    ): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        block(Client.ResultHandler { result ->
            if (result is T) {
                continuation.resumeWith(Result.success(result))
            } else if (result is TdApi.Error) {
                Log.w(TAG, "TDLib async error: ${result.message} (${result.code})")
                @Suppress("UNCHECKED_CAST")
                continuation.resumeWith(Result.success(result as T))
            } else {
                @Suppress("UNCHECKED_CAST")
                continuation.resumeWith(Result.success(result as T))
            }
        })
    }
}
