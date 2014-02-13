/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.generators.injectors

import com.intellij.openapi.project.Project
import org.jetbrains.jet.context.GlobalContext
import org.jetbrains.jet.context.GlobalContextImpl
import org.jetbrains.jet.lang.PlatformToKotlinClassMap
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor
import org.jetbrains.jet.lang.descriptors.ModuleDescriptorImpl
import org.jetbrains.jet.lang.resolve.*
import org.jetbrains.jet.lang.resolve.calls.CallResolver
import org.jetbrains.jet.lang.resolve.calls.CallResolverExtensionProvider
import org.jetbrains.jet.lang.resolve.java.JavaClassFinderImpl
import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver
import org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap
import org.jetbrains.jet.lang.resolve.java.resolver.*
import org.jetbrains.jet.lang.resolve.kotlin.VirtualFileFinder
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession
import org.jetbrains.jet.lang.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.jet.lang.types.DependencyClassByQualifiedNameResolverDummyImpl
import org.jetbrains.jet.lang.types.expressions.ExpressionTypingServices
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns
import org.jetbrains.jet.storage.StorageManager
import org.jetbrains.jet.di.*
import kotlin.properties.Delegates

// NOTE: After making changes, you need to re-generate the injectors.
//       To do that, you can run main in this file.
public fun main(args: Array<String>) {
    for (generator in injectorGenerators) {
        try {
            generator.generate()
        }
        catch (e: Throwable) {
            System.err.println(generator.getOutputFile())
            throw e
        }
    }
}

public val injectorGenerators: List<DependencyInjectorGenerator> by Delegates.lazy {
    GenerateInjectors().generators
}

public class GenerateInjectors {

    private fun DependencyInjectorGenerator.commonForTopDownAnalyzer() {
        publicFields(
                javaClass<TopDownAnalyzer>(),
                javaClass<TopDownAnalysisContext>(),
                javaClass<BodyResolver>(),
                javaClass<ControlFlowAnalyzer>(),
                javaClass<DeclarationsChecker>(),
                javaClass<DescriptorResolver>()
        )
        field(javaClass<StorageManager>(), init = GivenExpression("topDownAnalysisParameters.getStorageManager()"))
        field(javaClass<CallResolverExtensionProvider>())

        publicParameter(javaClass<Project>())
        parameter(javaClass<TopDownAnalysisParameters>())
        publicParameters(
                javaClass<BindingTrace>(),
                javaClass<ModuleDescriptorImpl>()
        )
    }

    private val generatorForTopDownAnalyzerBasic =
            generator("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForTopDownAnalyzerBasic", javaClass<GenerateInjectors>()) {
                commonForTopDownAnalyzer()
                fields(
                        javaClass<DependencyClassByQualifiedNameResolverDummyImpl>(),
                        javaClass<MutablePackageFragmentProvider>()
                )
                parameter(javaClass<PlatformToKotlinClassMap>())
            }

    private val generatorForTopDownAnalyzerForJs =
            generator("js/js.translator/src", "org.jetbrains.jet.di", "InjectorForTopDownAnalyzerForJs", javaClass<GenerateInjectors>()) {
                commonForTopDownAnalyzer()
                fields(
                        javaClass<DependencyClassByQualifiedNameResolverDummyImpl>(),
                        javaClass<MutablePackageFragmentProvider>()
                )
                field(javaClass<PlatformToKotlinClassMap>(), init = GivenExpression("org.jetbrains.jet.lang.PlatformToKotlinClassMap.EMPTY"))
            }

    private val generatorForTopDownAnalyzerForJvm =
            generator("compiler/frontend.java/src", "org.jetbrains.jet.di", "InjectorForTopDownAnalyzerForJvm", javaClass<GenerateInjectors>()) {
                implementInterface(javaClass<InjectorForTopDownAnalyzer>())
                commonForTopDownAnalyzer()
                publicField(javaClass<JavaDescriptorResolver>())
                field(javaClass<JavaToKotlinClassMap>(), init = GivenExpression("org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap.getInstance()"))
                fields(
                        javaClass<JavaClassFinderImpl>(),
                        javaClass<TraceBasedExternalSignatureResolver>(),
                        javaClass<TraceBasedJavaResolverCache>(),
                        javaClass<TraceBasedErrorReporter>(),
                        javaClass<PsiBasedMethodSignatureChecker>(),
                        javaClass<PsiBasedExternalAnnotationResolver>(),
                        javaClass<MutablePackageFragmentProvider>()
                )
                field(javaClass<VirtualFileFinder>(), init = GivenExpression(javaClass<VirtualFileFinder>().getName() + ".SERVICE.getInstance(project)"))
            }

    private val generatorForJavaDescriptorResolver =
            generator("compiler/frontend.java/src", "org.jetbrains.jet.di", "InjectorForJavaDescriptorResolver", javaClass<GenerateInjectors>()) {

                parameters(
                        javaClass<Project>(),
                        javaClass<BindingTrace>()
                )

                publicField(javaClass<GlobalContextImpl>(),
                            init = GivenExpression("org.jetbrains.jet.context.ContextPackage.GlobalContext()"))
                field(javaClass<StorageManager>(), init = GivenExpression("globalContext.getStorageManager()"))
                publicField(javaClass<JavaClassFinderImpl>())
                fields(
                        javaClass<TraceBasedExternalSignatureResolver>(),
                        javaClass<TraceBasedJavaResolverCache>(),
                        javaClass<TraceBasedErrorReporter>(),
                        javaClass<PsiBasedMethodSignatureChecker>(),
                        javaClass<PsiBasedExternalAnnotationResolver>()
                )
                publicField(javaClass<JavaDescriptorResolver>())
                field(javaClass<VirtualFileFinder>(),
                      init = GivenExpression(javaClass<VirtualFileFinder>().getName() + ".SERVICE.getInstance(project)"))
                publicField(javaClass<ModuleDescriptorImpl>(), name = "module",
                            init = GivenExpression("org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM.createJavaModule(\"<fake-jdr-module>\")"))
            }

    private val generatorForMacro =
            generator("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForMacros", javaClass<GenerateInjectors>()) {
                publicField(javaClass<ExpressionTypingServices>())
                field(javaClass<CallResolverExtensionProvider>())
                field(javaClass<GlobalContext>(), init = GivenExpression("org.jetbrains.jet.context.ContextPackage.GlobalContext()"))
                field(javaClass<StorageManager>(), init = GivenExpression("globalContext.getStorageManager()"))
                field(javaClass<PlatformToKotlinClassMap>(), init = GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"))

                publicParameter(javaClass<Project>())
                parameter(javaClass<ModuleDescriptor>())
            }

    private val generatorForTests =
            generator("compiler/tests", "org.jetbrains.jet.di", "InjectorForTests", javaClass<GenerateInjectors>()) {
                publicFields(
                        javaClass<DescriptorResolver>(),
                        javaClass<ExpressionTypingServices>(),
                        javaClass<TypeResolver>(),
                        javaClass<CallResolver>()
                )
                field(javaClass<CallResolverExtensionProvider>())
                field(javaClass<StorageManager>(), init = GivenExpression("globalContext.getStorageManager()"))
                publicField(javaClass<KotlinBuiltIns>(), init = GivenExpression("KotlinBuiltIns.getInstance()"))
                field(javaClass<PlatformToKotlinClassMap>(), init = GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"))
                field(javaClass<GlobalContext>(), init = GivenExpression("org.jetbrains.jet.context.ContextPackage.GlobalContext()"))

                publicParameter(javaClass<Project>())
                parameter(javaClass<ModuleDescriptor>())
            }

    private val generatorForBodyResolve =
            generator("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForBodyResolve", javaClass<GenerateInjectors>()) {
                publicField(javaClass<BodyResolver>())
                field(javaClass<CallResolverExtensionProvider>())
                field(javaClass<PlatformToKotlinClassMap>(), init = GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"))
                field(javaClass<FunctionAnalyzerExtension>())
                field(javaClass<StorageManager>(), init = GivenExpression("topDownAnalysisParameters.getStorageManager()"))

                publicParameter(javaClass<Project>())
                parameter(javaClass<TopDownAnalysisParameters>())
                publicParameters(
                        javaClass<BindingTrace>(),
                        javaClass<BodiesResolveContext>()
                )
                parameter(javaClass<ModuleDescriptor>())
            }

    private val generatorForLazyResolve =
            generator("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForLazyResolve", javaClass<GenerateInjectors>()) {
                parameters(
                        javaClass<Project>(),
                        javaClass<GlobalContextImpl>(),
                        javaClass<ModuleDescriptorImpl>(),
                        javaClass<DeclarationProviderFactory>(),
                        javaClass<BindingTrace>()
                )

                publicField(javaClass<ResolveSession>())
                field(javaClass<CallResolverExtensionProvider>())
                field(javaClass<StorageManager>(), init = GivenExpression("resolveSession.getStorageManager()"))
                field(javaClass<PlatformToKotlinClassMap>(), init = GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"))
            }

    val generators = listOf(
            generatorForTopDownAnalyzerBasic,
            generatorForTopDownAnalyzerForJvm,
            generatorForJavaDescriptorResolver,
            generatorForTopDownAnalyzerForJs,
            generatorForMacro,
            generatorForTests,
            generatorForLazyResolve,
            generatorForBodyResolve
    )
}
