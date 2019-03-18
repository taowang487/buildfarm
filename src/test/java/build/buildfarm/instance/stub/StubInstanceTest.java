// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buildfarm.instance.stub;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import build.bazel.remote.execution.v2.Action;
import build.bazel.remote.execution.v2.ActionCacheGrpc.ActionCacheImplBase;
import build.bazel.remote.execution.v2.ActionResult;
import build.bazel.remote.execution.v2.ContentAddressableStorageGrpc.ContentAddressableStorageImplBase;
import build.bazel.remote.execution.v2.FindMissingBlobsRequest;
import build.bazel.remote.execution.v2.FindMissingBlobsResponse;
import build.bazel.remote.execution.v2.GetActionResultRequest;
import build.bazel.remote.execution.v2.Digest;
import build.bazel.remote.execution.v2.UpdateActionResultRequest;
import build.buildfarm.common.DigestUtil;
import build.buildfarm.common.DigestUtil.ActionKey;
import build.buildfarm.instance.Instance;
import build.buildfarm.instance.stub.ByteStreamUploader;
import com.google.bytestream.ByteStreamGrpc.ByteStreamImplBase;
import com.google.bytestream.ByteStreamProto.WriteRequest;
import com.google.bytestream.ByteStreamProto.WriteResponse;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.util.MutableHandlerRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StubInstanceTest {
  private static final DigestUtil DIGEST_UTIL = new DigestUtil(DigestUtil.HashFunction.SHA256);

  private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
  private final String fakeServerName = "fake server for " + getClass();
  private Server fakeServer;

  @Before
  public void setUp() throws IOException {
    fakeServer =
        InProcessServerBuilder.forName(fakeServerName)
            .fallbackHandlerRegistry(serviceRegistry)
            .directExecutor()
            .build()
            .start();
  }

  @After
  public void tearDown() throws InterruptedException {
    fakeServer.shutdownNow();
    fakeServer.awaitTermination();
  }

  @Test
  public void reflectsNameAndDigestUtil() {
    String test1Name = "test1";
    ByteString test1Blob = ByteString.copyFromUtf8(test1Name);
    DigestUtil test1DigestUtil = new DigestUtil(DigestUtil.HashFunction.SHA256);
    Instance test1Instance = new StubInstance(
        test1Name,
        test1DigestUtil,
        /* channel=*/ null,
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    assertThat(test1Instance.getName()).isEqualTo(test1Name);
    assertThat(test1Instance.getDigestUtil().compute(test1Blob))
        .isEqualTo(test1DigestUtil.compute(test1Blob));

    /* and once again to verify that those values change due to inputs */
    String test2Name = "test2";
    ByteString test2Blob = ByteString.copyFromUtf8(test2Name);
    DigestUtil test2DigestUtil = new DigestUtil(DigestUtil.HashFunction.MD5);
    Instance test2Instance = new StubInstance(
        test2Name,
        test2DigestUtil,
        /* channel=*/ null,
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    assertThat(test2Instance.getName()).isEqualTo(test2Name);
    assertThat(test2Instance.getDigestUtil().compute(test2Blob))
        .isEqualTo(test2DigestUtil.compute(test2Blob));
  }

  @Test
  public void getActionResultReturnsNullForNotFound() {
    AtomicReference<GetActionResultRequest> reference = new AtomicReference<>();
    serviceRegistry.addService(
        new ActionCacheImplBase() {
          @Override
          public void getActionResult(GetActionResultRequest request, StreamObserver<ActionResult> responseObserver) {
            reference.set(request);
            responseObserver.onError(Status.NOT_FOUND.asException());
          }
        });
    Instance instance = new StubInstance(
        "test",
        DIGEST_UTIL,
        InProcessChannelBuilder.forName(fakeServerName).directExecutor().build(),
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    ActionKey actionKey = DIGEST_UTIL.computeActionKey(Action.getDefaultInstance());
    assertThat(instance.getActionResult(actionKey)).isNull();
    GetActionResultRequest request = reference.get();
    assertThat(request.getInstanceName()).isEqualTo(instance.getName());
    assertThat(request.getActionDigest()).isEqualTo(actionKey.getDigest());
  }

  @Test
  public void putActionResultCallsUpdateActionResult() throws InterruptedException {
    AtomicReference<UpdateActionResultRequest> reference = new AtomicReference<>();
    serviceRegistry.addService(
        new ActionCacheImplBase() {
          @Override
          public void updateActionResult(UpdateActionResultRequest request, StreamObserver<ActionResult> responseObserver) {
            reference.set(request);
            responseObserver.onNext(request.getActionResult());
            responseObserver.onCompleted();
          }
        });
    String instanceName = "putActionResult-test";
    Instance instance = new StubInstance(
        instanceName,
        DIGEST_UTIL,
        InProcessChannelBuilder.forName(fakeServerName).directExecutor().build(),
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    ActionKey actionKey = DigestUtil.asActionKey(Digest.newBuilder()
        .setHash("action-digest")
        .setSizeBytes(1)
        .build());
    ActionResult actionResult = ActionResult.getDefaultInstance();
    instance.putActionResult(actionKey, actionResult);
    UpdateActionResultRequest request = reference.get();
    assertThat(request.getInstanceName()).isEqualTo(instanceName);
    assertThat(request.getActionDigest()).isEqualTo(actionKey.getDigest());
    assertThat(request.getActionResult()).isEqualTo(actionResult);
  }

  @Test
  public void findMissingBlobsCallsFindMissingBlobs()
      throws ExecutionException, InterruptedException {
    AtomicReference<FindMissingBlobsRequest> reference = new AtomicReference<>();
    serviceRegistry.addService(
        new ContentAddressableStorageImplBase() {
          @Override
          public void findMissingBlobs(FindMissingBlobsRequest request, StreamObserver<FindMissingBlobsResponse> responseObserver) {
            reference.set(request);
            responseObserver.onNext(FindMissingBlobsResponse.getDefaultInstance());
            responseObserver.onCompleted();
          }
        });
    String instanceName = "findMissingBlobs-test";
    Instance instance = new StubInstance(
        instanceName,
        DIGEST_UTIL,
        InProcessChannelBuilder.forName(fakeServerName).directExecutor().build(),
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    Iterable<Digest> digests = ImmutableList.of(
        Digest.newBuilder().setHash("present").setSizeBytes(1).build());
    assertThat(instance.findMissingBlobs(digests, newDirectExecutorService()).get()).isEmpty();
  }

  @Test
  public void outputStreamWrites() throws IOException {
    AtomicReference<ByteString> writtenContent = new AtomicReference<>();
    serviceRegistry.addService(
        new ByteStreamImplBase() {
          @Override
          public StreamObserver<WriteRequest> write(StreamObserver<WriteResponse> responseObserver) {
            return new StreamObserver<WriteRequest>() {
              ByteString content = ByteString.EMPTY;
              boolean finished = false;

              @Override
              public void onNext(WriteRequest request) {
                checkState(!finished);
                if (request.getData().size() != 0) {
                  checkState(request.getWriteOffset() == content.size());
                  content = content.concat(request.getData());
                }
                finished = request.getFinishWrite();
                if (finished) {
                  writtenContent.set(content);
                  responseObserver.onNext(WriteResponse.newBuilder()
                      .setCommittedSize(content.size())
                      .build());
                }
              }

              @Override
              public void onError(Throwable t) {
                t.printStackTrace();
              }

              @Override
              public void onCompleted() {
                responseObserver.onCompleted();
              }
            };
          }
        });
    String instanceName = "outputStream-test";
    Instance instance = new StubInstance(
        instanceName,
        DIGEST_UTIL,
        InProcessChannelBuilder.forName(fakeServerName).directExecutor().build(),
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    String resourceName = "output-stream-test";
    ByteString content = ByteString.copyFromUtf8("test-content");
    try (OutputStream out = instance.getStreamOutput(resourceName, -1)) {
      out.write(content.toByteArray());
    }
    assertThat(writtenContent.get()).isEqualTo(content);
  }

  @Test
  public void completedWriteBeforeCloseThrowsOnNextInteraction() throws IOException {
    AtomicReference<ByteString> writtenContent = new AtomicReference<>();
    serviceRegistry.addService(
        new ByteStreamImplBase() {
          @Override
          public StreamObserver<WriteRequest> write(StreamObserver<WriteResponse> responseObserver) {
            return new StreamObserver<WriteRequest>() {
              boolean completed = false;

              @Override
              public void onNext(WriteRequest request) {
                if (!completed) {
                  responseObserver.onNext(WriteResponse.newBuilder()
                      .setCommittedSize(request.getData().size())
                      .build());
                  responseObserver.onCompleted();
                  completed = true;
                }
              }

              @Override
              public void onError(Throwable t) {
                t.printStackTrace();
              }

              @Override
              public void onCompleted() {
              }
            };
          }
        });
    String instanceName = "early-completed-outputStream-test";
    Instance instance = new StubInstance(
        instanceName,
        DIGEST_UTIL,
        InProcessChannelBuilder.forName(fakeServerName).directExecutor().build(),
        Long.MAX_VALUE, NANOSECONDS,
        /* uploader=*/ null);
    String resourceName = "early-completed-output-stream-test";
    ByteString content = ByteString.copyFromUtf8("test-content");
    boolean writeThrewException = false;
    try (OutputStream out = instance.getStreamOutput(resourceName, -1)) {
      out.write(content.toByteArray());
      try {
        out.write(content.toByteArray()); // should throw
      } catch (Exception e) {
        e.printStackTrace();
        writeThrewException = true;
      }
    }
    assertThat(writeThrewException).isTrue();
  }
}
