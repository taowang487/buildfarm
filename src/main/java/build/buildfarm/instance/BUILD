load("@rules_java//java:java_library.bzl", "java_library")

java_library(
    name = "instance",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//src/main/java/build/buildfarm/common",
        "//src/main/protobuf/build/buildfarm/v1test:buildfarm_java_proto",
        "@googleapis//google/longrunning:longrunning_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_failureaccess",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_protobuf_protobuf_java_util",
        "@maven//:io_grpc_grpc_api",
        "@maven//:io_grpc_grpc_core",
        "@maven//:io_grpc_grpc_protobuf",
        "@maven//:io_grpc_grpc_stub",
        "@remoteapis//build/bazel/remote/execution/v2:remote_execution_java_proto",
    ],
)
