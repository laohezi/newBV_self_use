
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.google.protobuf)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.grpc.kotlin.stub)
    api(libs.grpc.okhttp)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.protobuf.kotlin)
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.truth)
}

sourceSets["main"].proto {
    srcDir("./proto")
    ProtobufConfiguration.excludeProtoFiles.forEach(::exclude)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        // 代码生成器必须与 grpc 运行时同版本：1.72.0 及更早的 osx-aarch_64 构件
        // 实为 x86_64 二进制，在 Apple Silicon 上无法执行（上游打包问题，1.76.0 起修复）
        create("java") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.76.0"
        }
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.76.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                named("java") {
                }
                create("kotlin") {
                }
            }
            it.plugins {
                create("grpc") {
                }
                create("grpckt") {
                }
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
