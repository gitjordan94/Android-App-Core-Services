package app.core.services.common

import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import java.io.Serializable

fun <T> Map<String, T>.toBundle(bundle: Bundle = Bundle()) = bundle.apply {
    forEach {
        when (val value = it.value) {
            is Bundle -> putBundle(it.key, value)
            is Byte -> putByte(it.key, value)
            is ByteArray -> putByteArray(it.key, value)
            is Char -> putChar(it.key, value)
            is CharArray -> putCharArray(it.key, value)
            is CharSequence -> putCharSequence(it.key, value)
            is Float -> putFloat(it.key, value)
            is FloatArray -> putFloatArray(it.key, value)
            is Short -> putShort(it.key, value)
            is ShortArray -> putShortArray(it.key, value)
            is Int -> putInt(it.key, value)
            is IntArray -> putIntArray(it.key, value)
            is Double -> putDouble(it.key, value)
            is DoubleArray -> putDoubleArray(it.key, value)
            is Size -> putSize(it.key, value)
            is SizeF -> putSizeF(it.key, value)
            is IBinder -> putBinder(it.key, value)
            is Serializable -> putSerializable(it.key, value)
            is Parcelable -> putParcelable(it.key, value)
            else -> putString(it.key, value?.toString())
        }
    }
}

fun <K, V : Any> mapOfNotNull(vararg pairs: Pair<K, V?>): Map<K, V> {
    if (pairs.isEmpty()) return mapOf()

    return buildMap {
        for ((key, value) in pairs) {
            if (value == null) continue

            put(key, value)
        }
    }
}