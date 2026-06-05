package app.core.services.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.core.services.attribution.DefaultMediaSourceParser
import app.core.services.attribution.MediaSourceParser
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber

internal class PreferencesDataStore(
    private val preferences: DataStore<Preferences>,
    private val mediaSourceParser: MediaSourceParser = DefaultMediaSourceParser,
) {
    private val keyFirstLaunch = booleanPreferencesKey("is_first_launch")
    private val keyMediaSource = stringPreferencesKey("media_source")
    private val keyCampaignName = stringPreferencesKey("campaign_name")
    private val keyAdSet = stringPreferencesKey("ad_set")
    private val keyAdGroup = stringPreferencesKey("ad_group")
    private val keyDeepLinkValue = stringPreferencesKey("deep_link_value")
    private val keyInstallReferrer = stringPreferencesKey("gp_referrer")

    suspend fun isFirstLaunch(): Boolean {
        return getValue(keyFirstLaunch) ?: true
    }

    suspend fun setFirstLaunch(isFirstLaunch: Boolean) {
        setValue(keyFirstLaunch, isFirstLaunch)
    }

    suspend fun getInstallReferrer() = getValue(keyInstallReferrer)

    suspend fun setInstallReferrer(installReferrer: String) {
        setValue(keyInstallReferrer, installReferrer)
    }

    suspend fun setAttributionData(attribution: Attribution) {
        try {
            preferences.edit {
                it[keyMediaSource] = attribution.mediaSource.value

                if (attribution.campaign != null) {
                    it[keyCampaignName] = attribution.campaign
                }

                if (attribution.ad != null) {
                    it[keyAdSet] = attribution.ad
                }

                if (attribution.adGroup != null) {
                    it[keyAdGroup] = attribution.adGroup
                }

                if (attribution.deepLinkValue != null) {
                    it[keyDeepLinkValue] = attribution.deepLinkValue
                }
            }
        } catch (e: Throwable) {
            Timber.e(e)
        }
    }

    suspend fun getAttributionData(): Attribution? {
        return try {
            preferences.data
                .map {
                    val mediaSource = it[keyMediaSource]

                    if (mediaSource != null) {
                        Attribution(
                            mediaSource = mediaSourceParser.parse(mediaSource),
                            campaign = it[keyCampaignName],
                            adGroup = it[keyAdGroup],
                            ad = it[keyAdSet],
                            deepLinkValue = it[keyDeepLinkValue],
                            attributionSource = AttributionSource.APPSFLYER
                        )
                    } else {
                        null
                    }
                }
                .firstOrNull()
        } catch (e: Throwable) {
            Timber.e(e)
            null
        }
    }

    private suspend fun <T> getValue(key: Preferences.Key<T>): T? {
        return try {
            preferences.data
                .map { it[key] }
                .firstOrNull()
        } catch (e: Throwable) {
            Timber.e(e)
            null
        }
    }

    private suspend fun <T> setValue(key: Preferences.Key<T>, value: T?) {
        try {
            preferences.edit {
                if (value != null) {
                    it[key] = value
                } else {
                    it -= key
                }
            }
        } catch (e: Throwable) {
            Timber.e(e)
        }
    }
}