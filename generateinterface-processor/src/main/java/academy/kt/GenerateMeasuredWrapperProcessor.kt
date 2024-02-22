package academy.kt

import com.squareup.javapoet.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.AnnotatedConstruct
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror


class GenerateMeasuredWrapperProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes(): Set<String> =
        setOf(Measured::class.qualifiedName!!)

    override fun getSupportedSourceVersion(): SourceVersion =
        SourceVersion.latestSupported()

    override fun process(
        annotations: Set<TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        roundEnv.getElementsAnnotatedWith(Measured::class.java)
            .filterIsInstance<ExecutableElement>()
            .groupBy { it.enclosingElement!! }
            .forEach { (clazz) -> generateMeasuredClass(clazz) }
        return true
    }

    private fun generateMeasuredClass(classElement: Element) {
        val className = classElement.simpleName.toString()
        val measuredName = "Measured$className"
        val measuredPackage = processingEnv.elementUtils
            .getPackageOf(classElement)
            .qualifiedName
            .toString()
        val publicMethods = classElement.enclosedElements
            .filter { it.kind == ElementKind.METHOD }
            .filter { it.modifiers.contains(Modifier.PUBLIC) }
            .filterIsInstance<ExecutableElement>()
        val constructorParameters = classElement.enclosedElements
            .filter { it.kind == ElementKind.CONSTRUCTOR }
            .filterIsInstance<ExecutableElement>()
            .flatMap { it.parameters }

        JavaFile.builder(
            measuredPackage,
            TypeSpec
                .classBuilder(measuredName)
                .addField(
                    FieldSpec.builder(
                        classElement.asType().toTypeSpec(),
                        "wrapper",
                        Modifier.PRIVATE
                    ).build()
                )
                .addMethod(
                    MethodSpec.constructorBuilder()
                        .addParameter(
                            classElement.asType().toTypeSpec(),
                            "wrapper"
                        )
                        .addStatement("this.wrapper = wrapper")
                        .build()
                )
                .addMethod(
                    MethodSpec.constructorBuilder()
                        .addParameters(constructorParameters
                            .filterIsInstance<VariableElement>()
                            .map {
                                buildMethodParameter(it)
                            })
                        .addStatement(
                            "this.wrapper = new ${
                                classElement.simpleName
                            }(${
                                constructorParameters
                                    .joinToString { it.simpleName.toString() }
                            })"
                        )
                        .build()
                )
                .addMethods(publicMethods.map {
                    buildInterfaceMethod(className, it)
                }
                )
                .build()
        ).build()
            .writeTo(processingEnv.filer)
    }

    private fun buildInterfaceMethod(
        className: String,
        method: ExecutableElement
    ): MethodSpec {
        val methodName = method.simpleName.toString()
        return MethodSpec
            .methodBuilder(methodName)
            .addModifiers(method.modifiers)
            .addParameters(
                method.parameters
                    .map(::buildMethodParameter)
            )
            .returns(
                TypeName.get(method.returnType)
                    .annotated(
                        method.returnType.getAnnotationSpecs()
                    )
            )
            .addAnnotations(method
                .annotationMirrors
                .filter { !isMeasured(it) }
                .map(AnnotationSpec::get))
            .addCode(run {
                val params = method.parameters
                    .joinToString { it.simpleName }
                
                if (method.annotationMirrors.none(::isMeasured))
                    "return wrapper.$methodName($params);"
                else {
                    val retType = method.returnType
                    """
                    long before = System.currentTimeMillis();
                    $retType value = wrapper.$methodName($params);
                    long after = System.currentTimeMillis();
                    System.out.println("$methodName from $className took " + (after - before) + " ms");
                    return value;
                    """.trimIndent()
                }
            })
            .build()
    }

    private fun isMeasured(it: AnnotationMirror) =
        (it.annotationType.asElement() as TypeElement)
            .qualifiedName
            ?.toString() == Measured::class.qualifiedName

    private fun buildMethodParameter(
        variableElement: VariableElement
    ): ParameterSpec {
        val asType = variableElement.asType()
        return ParameterSpec
            .builder(
                TypeName.get(asType)
                    .annotated(asType.getAnnotationSpecs()),
                variableElement.simpleName.toString()
            )
            .addAnnotations(variableElement.getAnnotationSpecs())
            .build()
    }
}

private fun TypeMirror.toTypeSpec() = TypeName.get(this)
    .annotated(this.getAnnotationSpecs())

private fun AnnotatedConstruct.getAnnotationSpecs() =
    annotationMirrors.map(AnnotationSpec::get)
