package es.jvbabi.docker.kt.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Serializable(with = OptionalSerializer::class)
internal sealed class Optional<out T> {
    data object Undefined : Optional<Nothing>()
    data class Defined<out T>(val value: T) : Optional<T>()
}

@OptIn(ExperimentalContracts::class)
internal fun <T> Optional<T>.isDefined(): Boolean {
    contract {
        returns(true) implies (this@isDefined is Optional.Defined<T>)
    }
    return this is Optional.Defined<T>
}

internal class OptionalSerializer<T>(private val serializer: KSerializer<T>) : KSerializer<Optional<T>> {
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: Optional<T>) {
        when (value) {
            is Optional.Defined -> encoder.encodeSerializableValue(serializer, value.value)
            is Optional.Undefined -> {} // Do nothing if Undefined
        }
    }

    override fun deserialize(decoder: Decoder): Optional<T> {
        return Optional.Defined(decoder.decodeSerializableValue(serializer))
    }
}