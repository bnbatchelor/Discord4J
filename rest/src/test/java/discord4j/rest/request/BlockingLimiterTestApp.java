/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J. If not, see <http://www.gnu.org/licenses/>.
 */

package discord4j.rest.request;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.time.Duration;
import java.util.Arrays;

public class BlockingLimiterTestApp {

    private static final Logger log = Loggers.getLogger(BlockingLimiterTestApp.class);

    public static void main(String[] args) {
        for (GlobalRateLimiter rateLimiter : Arrays.asList(
                BucketGlobalRateLimiter.create(),
                new ClockGlobalRateLimiter(50, Duration.ofSeconds(1), Schedulers.parallel()),
                new SemaphoreGlobalRateLimiter(16),
                new UnboundedGlobalRateLimiter()
        )) {
            log.info("Testing {}", rateLimiter.getClass().toString());
            Flux.range(0, 100)
                    .flatMap(index -> rateLimiter.withLimiter(Mono.defer(() -> {
                        // simulate a request
                        return Mono.delay(Duration.ofMillis(50))
                                .flatMap(tick -> {
                                    // if this is the 50th index, we trip GRL
                                    if (index == 50) {
                                        log.info("Activating global rate limiter");
                                        return rateLimiter.rateLimitFor(Duration.ofMillis(3000)).thenReturn(index);
                                    }
                                    return Mono.just(index);
                                });
                    })), 16)
                    .collectList()
                    .elapsed()
                    .doOnNext(TupleUtils.consumer(
                            (time, list) -> log.info("Sent {} messages in {} milliseconds ({} messages/s)",
                                    list.size(), time, (list.size() / (double) time) * 1000)))
                    .block();
        }
    }
}
