package es.jvbabi.docker.kt.util

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
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
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Optional", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Optional<T>) {
        when (value) {
            is Optional.Defined -> encoder.encodeSerializableValue(serializer, value.value)
            is Optional.Undefined -> {}
        }
    }

    override fun deserialize(decoder: Decoder): Optional<T> {
        return Optional.Defined(decoder.decodeSerializableValue(serializer))
    }
}