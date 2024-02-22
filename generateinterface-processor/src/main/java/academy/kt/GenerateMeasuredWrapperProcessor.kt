package academy.kt

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic


class GenerateMeasuredWrapperProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes(): Set<String> =
        setOf(Measured::class.qualifiedName!!)

    override fun getSupportedSourceVersion(): SourceVersion =
        SourceVersion.latestSupported()

    override fun process(
        annotations: Set<TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Implement GenerateMeasuredWrapperProcessor")
        return true
    }
}
