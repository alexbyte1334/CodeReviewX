package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.service.ReviewTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoExecutionWorkerTest {

    @Test
    void enforcesHardDeadlineAndStopsHeartbeat() throws Exception {
        DemoStore store = mock(DemoStore.class);
        ReviewTaskService reviewTasks = mock(ReviewTaskService.class);
        ExecutorService dispatcher = mock(ExecutorService.class);
        ExecutorService pipeline = mock(ExecutorService.class);
        ScheduledExecutorService heartbeats = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        Future<ReviewTaskResponse> review = mock(Future.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        when(pipeline.submit(any(java.util.concurrent.Callable.class))).thenReturn(review);
        doReturn(heartbeat).when(heartbeats).scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        when(review.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new TimeoutException("deadline"));

        DemoProperties properties = new DemoProperties();
        properties.setExecutionDeadline(Duration.ofMillis(5));
        DemoExecutionWorker worker = new DemoExecutionWorker(
                store, properties, reviewTasks, dispatcher, pipeline, heartbeats);
        DemoStore.Job job = new DemoStore.Job(1, 2, 1, "public", 3, 4);

        worker.execute(job);

        ArgumentCaptor<Runnable> heartbeatAction = ArgumentCaptor.forClass(Runnable.class);
        verify(heartbeats).scheduleAtFixedRate(
                heartbeatAction.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        heartbeatAction.getValue().run();

        verify(review).cancel(true);
        verify(heartbeat).cancel(false);
        verify(store).heartbeat(eq(job.id()), anyString(), any(LocalDateTime.class));
        verify(store).markFailed(
                eq(job), eq("DEMO_DEADLINE_EXCEEDED"), any(String.class));
    }
}
