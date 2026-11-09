package kotlinx.serialization
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE) annotation class Serializable
annotation class SerialName(val value: String)
annotation class Transient
