package com.zimbabeats.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.zimbabeats.family.ipc.IParentalControlCallback
import com.zimbabeats.family.ipc.IParentalControlService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the connection to the Parental Control companion app service.
 */
class ServiceConnectionManager(
    private val context: Context,
    private val companionChecker: CompanionAppChecker
) {
    companion object {
        private const val TAG = "ServiceConnManager"
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private var service: IParentalControlService? = null
    private var callback: IParentalControlCallback? = null
    private var isBound = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected: $name")
            service = IParentalControlService.Stub.asInterface(binder)
            _connectionState.value = ConnectionState.CONNECTED

            // Register callback if set
            callback?.let { cb ->
                try {
                    service?.registerCallback(cb)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to register callback", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected: $name")
            service = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "Binding died: $name")
            service = null
            _connectionState.value = ConnectionState.BINDING_DIED
            // Attempt reconnection
            unbind()
            bind()
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "Null binding: $name")
            _connectionState.value = ConnectionState.NULL_BINDING
        }
    }

    /**
     * Bind to the companion app service.
     * @return true if binding was initiated, false if companion not installed
     */
    fun bind(): Boolean {
        if (!companionChecker.isCompanionInstalled()) {
            Log.d(TAG, "Companion app not installed, skipping bind")
            _connectionState.value = ConnectionState.COMPANION_NOT_INSTALLED
            return false
        }

        if (isBound) {
            Log.d(TAG, "Already bound")
            return true
        }

        val intent = Intent(CompanionAppChecker.COMPANION_SERVICE_ACTION).apply {
            setPackage(CompanionAppChecker.COMPANION_PACKAGE_NAME)
        }

        return try {
            _connectionState.value = ConnectionState.CONNECTING
            isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!isBound) {
                Log.w(TAG, "bindService returned false")
                _connectionState.value = ConnectionState.BIND_FAILED
            }
            isBound
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception binding to service", e)
            _connectionState.value = ConnectionState.SECURITY_ERROR
            false
        }
    }

    /**
     * Unbind from the companion app service.
     */
    fun unbind() {
        if (isBound) {
            // Unregister callback first
            callback?.let { cb ->
                try {
                    service?.unregisterCallback(cb)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to unregister callback", e)
                }
            }

            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service not registered", e)
            }
            isBound = false
            service = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Get the service interface. Returns null if not connected.
     */
    fun getService(): IParentalControlService? = service

    /**
     * Check if service is connected.
     */
    fun isConnected(): Boolean = service != null && isBound

    /**
     * Check if service is currently connecting.
     */
    fun isConnecting(): Boolean = _connectionState.value == ConnectionState.CONNECTING

    /**
     * Set the callback to receive events from the companion app.
     */
    fun setCallback(callback: IParentalControlCallback?) {
        val previousCallback = this.callback
        this.callback = callback

        val svc = service ?: return
        try {
            // Unregister previous callback
            previousCallback?.let { cb -> svc.unregisterCallback(cb) }
            // Register new callback
            callback?.let { cb -> svc.registerCallback(cb) }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to update callback", e)
        }
    }

    /**
     * Connection state enum
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        BIND_FAILED,
        BINDING_DIED,
        NULL_BINDING,
        SECURITY_ERROR,
        COMPANION_NOT_INSTALLED
    }
}
