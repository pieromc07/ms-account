package com.nttdata.msaccount.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ReactiveBridgeTest {

    @Test
    void toSingleEmitsValue() {
        ReactiveBridge.toSingle(Mono.just("ok")).test()
                .assertValue("ok")
                .assertComplete();
    }

    @Test
    void toSingleFailsWhenMonoIsEmpty() {
        ReactiveBridge.toSingle(Mono.<String>empty()).test()
                .assertError(error -> error instanceof java.util.NoSuchElementException);
    }

    @Test
    void toMaybeEmitsValueOrCompletesEmpty() {
        Maybe<String> present = ReactiveBridge.toMaybe(Mono.just("ok"));
        Maybe<String> absent = ReactiveBridge.toMaybe(Mono.empty());

        present.test().assertValue("ok").assertComplete();
        absent.test().assertNoValues().assertComplete();
    }

    @Test
    void toFlowableEmitsAllElements() {
        io.reactivex.rxjava3.core.Flowable<Integer> flowable = ReactiveBridge.toFlowable(Flux.just(1, 2, 3));

        flowable.test().assertValues(1, 2, 3).assertComplete();
    }

    @Test
    void toMonoEmitsSingleValue() {
        Mono<String> mono = ReactiveBridge.toMono(Single.just("ok"));

        assertThat(mono.block(Duration.ofSeconds(1))).isEqualTo("ok");
    }

    @Test
    void toVoidCompletesWhenCompletableCompletes() {
        Mono<Void> mono = ReactiveBridge.toVoid(Completable.complete());

        assertThat(mono.block(Duration.ofSeconds(1))).isNull();
    }

    @Test
    void toVoidPropagatesCompletableError() {
        Mono<Void> mono = ReactiveBridge.toVoid(Completable.error(new IllegalStateException("boom")));

        TestObserver<Void> observer = Completable.fromPublisher(mono).test();
        observer.assertError(IllegalStateException.class);
    }
}
