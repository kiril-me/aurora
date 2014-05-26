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
package org.apache.aurora.scheduler.cron.quartz;

import java.util.Date;
import java.util.TimeZone;

import javax.inject.Inject;

import com.twitter.common.util.Clock;

import org.apache.aurora.scheduler.cron.CronPredictor;
import org.apache.aurora.scheduler.cron.CrontabEntry;
import org.quartz.CronExpression;

import static com.google.common.base.Preconditions.checkNotNull;

class CronPredictorImpl implements CronPredictor {
  private final Clock clock;
  private final TimeZone timeZone;

  @Inject
  CronPredictorImpl(Clock clock, TimeZone timeZone) {
    this.clock = checkNotNull(clock);
    this.timeZone = checkNotNull(timeZone);
  }

  @Override
  public Date predictNextRun(CrontabEntry schedule) {
    CronExpression cronExpression = Quartz.cronExpression(schedule, timeZone);
    return cronExpression.getNextValidTimeAfter(new Date(clock.nowMillis()));
  }
}
