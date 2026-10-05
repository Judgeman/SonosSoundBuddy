package de.paul.sonoscontrol

object SonosConfig {

    const val CLIENT_ID = "3707ab87-6e29-46c2-a3c1-cc2e3bc7a2c0"

    const val WORKER_CALLBACK_URL = "https://sonos-relay.82n2ck5mhv.workers.dev/callback"

    /** Adresse des Workers ohne Pfad. */
    val WORKER_BASE_URL = WORKER_CALLBACK_URL.removeSuffix("/callback")

    /** Endpunkt des Workers zum Erneuern abgelaufener Access-Tokens. */
    val WORKER_REFRESH_URL = "$WORKER_BASE_URL/refresh"

    const val OAUTH_SCOPE = "playback-control-all"

    /** Muss mit dem Intent-Filter in AndroidManifest.xml übereinstimmen. */
    const val APP_CALLBACK_SCHEME = "sonoscontrol"
    const val APP_CALLBACK_HOST = "callback"
}
