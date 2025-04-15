package tj.app.quran_todo.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable
data class SurahModel(
    val surahNumber: Int,
    val alphabetOrder: Int,
    val name: String,
    val ayats: Int,
    val revelationOrder: Int,
    val revelationPlace: String
)

@Serializable
data class SurahList(
    val surahs: List<SurahModel>
)

//object SurahListSerializer: KSerializer<SurahList> {
//    private val listSerializer = ListSerializer(SurahModel())
//
//    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SurahList") {
//        element("surahs", listSerializer.descriptor)
//    }
//
//    override fun serialize(encoder: Encoder, value: SurahList) {
//        encoder.encodeStructure(descriptor) {
//            encodeSerializableElement(descriptor, 0, listSerializer, value.surahs)
//        }
//    }
//
//    override fun deserialize(decoder: Decoder): SurahList {
//        return decoder.decodeStructure(descriptor) {
//            var surahs: List<SurahModel> = emptyList()
//
//            while (true) {
//                when (val index = decodeElementIndex(descriptor)) {
//                    CompositeDecoder.DECODE_DONE -> break
//                    0 -> surahs = decodeSerializableElement(descriptor, 0, listSerializer)
//                    else -> throw IllegalStateException("Unexpected index: $index")
//                }
//            }
//
//            SurahList(surahs)
//        }
//    }
//}