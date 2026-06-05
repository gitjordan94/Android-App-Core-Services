package app.core.services.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal interface KeyValueStorage {
    suspend fun putString(key: String, value: String?)

    suspend fun getString(key: String): String?

    suspend fun putBoolean(key: String, value: Boolean?)

    suspend fun getBoolean(key: String): Boolean?
}

internal class KeyValueStorageImpl(
    private val dataStore: DataStore<Preferences>
) : KeyValueStorage {

    override suspend fun putString(key: String, value: String?) {
        put(stringPreferencesKey(key), value)
    }

    override suspend fun getString(key: String): String? {
        return get(stringPreferencesKey(key))
    }

    override suspend fun putBoolean(key: String, value: Boolean?) {
        put(booleanPreferencesKey(key), value)
    }

    override suspend fun getBoolean(key: String): Boolean? {
        return get(booleanPreferencesKey(key))
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T?) {
        dataStore.edit { preferences ->
            if (value != null) {
                preferences[key] = value
            } else {
                preferences.remove(key)
            }
        }
    }

    private suspend fun <T> get(key: Preferences.Key<T>): T? {
        val preferences = dataStore.data.first()
        return preferences[key]
    }
}
