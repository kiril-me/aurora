/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aurora.scheduler.sla;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.twitter.common.application.modules.AppLauncherModule;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.base.Supplier;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stat;
import com.twitter.common.stats.StatsProvider;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.Clock;
import com.twitter.common.util.testing.FakeClock;

import org.apache.aurora.scheduler.base.Query;
import org.apache.aurora.scheduler.events.EventSink;
import org.apache.aurora.scheduler.events.PubsubEvent.SchedulerActive;
import org.apache.aurora.scheduler.state.PubsubTestUtil;
import org.apache.aurora.scheduler.storage.Storage;
import org.apache.aurora.scheduler.storage.testing.StorageTestUtil;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import static org.apache.aurora.gen.ScheduleStatus.PENDING;
import static org.easymock.EasyMock.expect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SlaModuleTest extends EasyMockTest {

  private Injector injector;
  private FakeClock clock;
  private StorageTestUtil storageUtil;
  private StatsProvider statsProvider;
  private SlaModule module;
  private EventSink eventSink;

  @Before
  public void setUp() throws Exception {
    storageUtil = new StorageTestUtil(this);
    clock = new FakeClock();
    statsProvider = createMock(StatsProvider.class);
    module = new SlaModule(Amount.of(5L, Time.MILLISECONDS));
    injector = Guice.createInjector(
        ImmutableList.<Module>builder()
            .add(module)
            .add(new LifecycleModule())
            .add(new AppLauncherModule())
            .add(new AbstractModule() {
              @Override
              protected void configure() {
                PubsubTestUtil.installPubsub(binder());
                bind(Clock.class).toInstance(clock);
                bind(Storage.class).toInstance(storageUtil.storage);
                bind(StatsProvider.class).toInstance(statsProvider);
              }
            }).build()
    );
    eventSink = PubsubTestUtil.startPubsub(injector);
  }

  @Test
  public void testNoSchedulingOnStart() {
    assertNotNull(module);

    control.replay();

    ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) injector.getInstance(
        Key.get(ScheduledExecutorService.class, SlaModule.SlaExecutor.class));

    assertEquals(0, executor.getQueue().size());
    assertEquals(0, executor.getActiveCount());
  }

  @Test
  public void testSchedulingOnEvent() throws Exception {
    assertNotNull(module);

    final CountDownLatch latch = new CountDownLatch(1);
    StatsProvider untracked = createMock(StatsProvider.class);
    expect(statsProvider.untracked()).andReturn(untracked).anyTimes();
    expect(untracked.makeGauge(EasyMock.anyString(), EasyMock.<Supplier<Number>>anyObject()))
        .andReturn(EasyMock.<Stat<Number>>anyObject())
        .andAnswer(new IAnswer<Stat<Number>>() {
          @Override
          public Stat<Number> answer() throws Throwable {
            latch.countDown();
            return null;
          }
        }).anyTimes();

    storageUtil.expectTaskFetch(
        Query.unscoped(),
        SlaTestUtil.makeTask(ImmutableMap.of(clock.nowMillis() - 1000, PENDING), 0)).anyTimes();
    storageUtil.expectOperations();

    control.replay();

    eventSink.post(new SchedulerActive());
    latch.await();
  }
}
