package me.trae.foundation.injector.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.trae.foundation.injector.api.annotation.Application;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ApplicationOrderTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void ordersDeferredApplicationsAfterTheirDependencies() {
        this.injector.initialize(new DependentApplication(), List.of(DependentFeature.class));
        this.injector.initialize(new CoreApplication(), List.of(FeatureConsumer.class, CoreFeature.class));

        assertEquals(List.of(this.injector.get(CoreFeature.class), this.injector.get(DependentFeature.class)), this.injector.getAll(Feature.class));
        assertSame(this.injector.get(DependentFeature.class), this.injector.get(FeatureConsumer.class).getFeatureList().getLast());
    }

    @Test
    void attachingToAnOlderApplicationDoesNotMakeItWin() {
        final CoreApplication coreApplication = new CoreApplication();
        this.injector.initialize(coreApplication, List.of(FeatureConsumer.class));
        this.injector.initialize(new DependentApplication(), List.of(DependentFeature.class));
        this.injector.attach(coreApplication, List.of(CoreFeature.class));

        assertEquals(List.of(this.injector.get(CoreFeature.class), this.injector.get(DependentFeature.class)), this.injector.getAll(Feature.class));
        assertSame(this.injector.get(DependentFeature.class), this.injector.get(FeatureConsumer.class).getFeatureList().getLast());
    }

    @AllArgsConstructor
    @Getter
    private static final class FeatureConsumer {

        private final List<Feature> featureList;
    }

    private interface Feature {

    }

    @NoArgsConstructor
    private static final class CoreFeature implements Feature {

    }

    @NoArgsConstructor
    private static final class DependentFeature implements Feature {

    }

    @NoArgsConstructor
    @Application
    private static final class CoreApplication {

    }

    @NoArgsConstructor
    @Application(dependencies = CoreApplication.class)
    private static final class DependentApplication {

    }
}