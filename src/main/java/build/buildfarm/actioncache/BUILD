load("@rules_java//java:java_library.bzl", "java_library")

java_library(
    name = "actioncache",
    srcs = glob(["*.java"]),
    plugins = ["//src/main/java/build/buildfarm/common:lombok"],
    visibility = ["//visibility:public"],
    deps = [
        "//src/main/java/build/buildfarm/backplane",
        "//src/main/java/build/buildfarm/common",
        "//src/main/java/build/buildfarm/common/config",
        "//src/main/java/build/buildfarm/common/resources",
        "//third_party/remote-apis:build_bazel_remote_execution_v2_remote_execution_java_grpc",
        "@maven//:com_github_ben_manes_caffeine_caffeine",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:io_grpc_grpc_api",
        "@maven//:io_grpc_grpc_core",
        "@maven//:net_javacrumbs_future_converter_future_converter_java8_guava",
        "@maven//:org_projectlombok_lombok",
        "@remoteapis//build/bazel/remote/execution/v2:remote_execution_java_proto",
    ],
)
