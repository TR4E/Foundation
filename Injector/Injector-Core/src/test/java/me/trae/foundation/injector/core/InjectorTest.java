package me.trae.foundation.injector.core;

import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.annotation.DependsOn;
import me.trae.foundation.injector.api.annotation.Provider;
import me.trae.foundation.injector.api.annotation.Singleton;
import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.exception.AmbiguousDependencyException;
import me.trae.foundation.injector.api.exception.ApplicationAlreadyInitializedException;
import me.trae.foundation.injector.api.exception.ApplicationNotAnnotatedException;
import me.trae.foundation.injector.api.exception.ApplicationNotInitializedException;
import me.trae.foundation.injector.api.exception.CircularDependencyException;
import me.trae.foundation.injector.api.exception.ComponentCreationException;
import me.trae.foundation.injector.api.exception.ConstructorException;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import me.trae.foundation.injector.api.exception.UnsupportedDependencyTypeException;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InjectorTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void serviceLoaderFindsCoreInjector() {
        assertInstanceOf(CoreInjector.class, Injector.INSTANCE);
        assertSame(this.injector, this.injector.get(Injector.class));
    }

    @Test
    void injectsConstructorDependenciesAsSingletons() {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(Service.class, AuditService.class, Repository.class));

        assertSame(this.injector.get(Repository.class), this.injector.get(Service.class).repository());
        assertSame(this.injector.get(Service.class).repository(), this.injector.get(AuditService.class).repository());
        assertSame(application, this.injector.get(Service.class).application());
    }

    @Test
    void createsScannedSingletons() {
        this.injector.initialize(new TestApplication());

        assertNotNull(this.injector.get(ScannedService.class));
        assertThrows(MissingDependencyException.class, () -> this.injector.get(Repository.class));
    }

    @Test
    void resolvesInterfacesAndRejectsBadGraphs() {
        this.injector.initialize(new TestApplication(), List.of(Greeting.class, EnglishGreeter.class));
        assertInstanceOf(EnglishGreeter.class, this.injector.get(Greeting.class).greeter());

        assertThrows(AmbiguousDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(Greeting.class, EnglishGreeter.class, FrenchGreeter.class)));
        assertThrows(MissingDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(Greeting.class)));
        assertThrows(CircularDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(Chicken.class, Egg.class)));
        assertThrows(ConstructorException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(TwoConstructors.class)));
        assertInstanceOf(IllegalStateException.class, assertThrows(ComponentCreationException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(Exploding.class))).getCause());
    }

    @Test
    void createsDependsOnTargetsFirst() {
        this.injector.initialize(new TestApplication(), List.of(First.class, Second.class));

        final List<Class<?>> componentList = this.injector.getComponents(TestApplication.class);
        assertTrue(componentList.indexOf(Second.class) < componentList.indexOf(First.class));
    }

    @Test
    void liveCollectionsFollowApplications() {
        this.injector.initialize(new TestApplication(), List.of(FeatureLookup.class, FirstFeature.class));

        final FeatureLookup featureLookup = this.injector.get(FeatureLookup.class);
        assertEquals(1, featureLookup.featureList().size());

        this.injector.initialize(new AddonApplication(), List.of(SecondFeature.class));
        assertEquals(2, featureLookup.featureList().size());
        assertEquals(2, featureLookup.featureSet().size());
        assertInstanceOf(SecondFeature.class, featureLookup.featureMap().get(SecondFeature.class));

        int visited = 0;
        for (final Feature _ : featureLookup.featureList()) {
            if (visited == 0) {
                this.injector.shutdown(new AddonApplication());
            }

            visited++;
        }

        assertEquals(2, visited);
        assertEquals(1, featureLookup.featureList().size());
    }

    @Test
    void collectsComponentsThatHoldLiveCollections() {
        this.injector.initialize(new TestApplication(), List.of(Dashboard.class, WidgetList.class, FirstFeature.class));

        assertEquals(1, this.injector.get(WidgetList.class).widgets().size());
    }

    @Test
    void rejectsUnsupportedCollections() {
        assertThrows(UnsupportedDependencyTypeException.class, () -> this.injector.initialize(new TestApplication(), List.of(NamedFeatureMap.class)));
    }

    @Test
    void registersProvidedValues() {
        this.injector.initialize(new TestApplication(), List.of(Connection.class, SettingsFactory.class, Repository.class));

        assertEquals("localhost", this.injector.get(Settings.class).host());
        assertSame(this.injector.get(Settings.class), this.injector.get(Connection.class).settings());
        assertTrue(this.injector.get(Pool.class).initialized);
        assertThrows(ComponentCreationException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(NullFactory.class)));
    }

    @Test
    void runsLifecycleInOrderAndReverse() {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(First.class, Second.class));
        this.injector.shutdown(application);

        assertEquals(List.of("initialize:Second", "initialize:First", "shutdown:First", "shutdown:Second"), application.eventList);
        assertThrows(ApplicationNotInitializedException.class, () -> this.injector.getComponents(TestApplication.class));
    }

    @Test
    void sorterDecidesCreationOrder() {
        this.injector.initialize(new SortedApplication(), List.of(Alpha.class, Beta.class));

        final List<Class<?>> componentList = this.injector.getComponents(SortedApplication.class);
        assertTrue(componentList.indexOf(Beta.class) < componentList.indexOf(Alpha.class));
    }

    @Test
    void rejectsBadApplications() {
        assertThrows(ApplicationNotAnnotatedException.class, () -> this.injector.initialize(new Object()));

        this.injector.initialize(new CoreApplication());
        assertThrows(ApplicationAlreadyInitializedException.class, () -> this.injector.initialize(new CoreApplication()));
    }

    @Test
    void defersApplicationsAndStopsDependents() {
        this.injector.initialize(new ClansApplication(), List.of(ClanManager.class));
        assertThrows(ApplicationNotInitializedException.class, () -> this.injector.getComponents(ClansApplication.class));

        this.injector.initialize(new CoreApplication(), List.of(Repository.class));
        assertSame(this.injector.get(Repository.class), this.injector.get(ClanManager.class).repository());

        this.injector.shutdown(new CoreApplication());
        assertThrows(ApplicationNotInitializedException.class, () -> this.injector.getComponents(ClansApplication.class));
    }

    @Test
    void failedStartRollsBack() {
        assertThrows(ComponentCreationException.class, () -> this.injector.initialize(new TestApplication(), List.of(Repository.class, Exploding.class)));

        assertThrows(ApplicationNotInitializedException.class, () -> this.injector.getComponents(TestApplication.class));
        assertThrows(MissingDependencyException.class, () -> this.injector.get(Repository.class));
        assertThrows(ComponentCreationException.class, () -> this.injector.initialize(new TestApplication(), List.of(Repository.class, Exploding.class)));
    }

    @Test
    void waitingApplicationFailureGoesToItsCallback() {
        final BrokenApplication brokenApplication = new BrokenApplication();

        this.injector.initialize(brokenApplication, List.of(Exploding.class));
        this.injector.initialize(new ClansApplication(), List.of(ClanManager.class));
        this.injector.initialize(new CoreApplication(), List.of(Repository.class));

        assertInstanceOf(ComponentCreationException.class, brokenApplication.failure);
        assertNotNull(this.injector.get(ClanManager.class));
    }

    @Test
    void extensionsSeeEveryLifecycleStepInOrder() {
        final ObservedApplication application = new ObservedApplication();

        this.injector.initialize(application, List.of(Alpha.class, Beta.class));
        this.injector.shutdown(application);

        assertEquals(List.of("create:ScannedService", "create:Alpha", "create:Beta", "initialize", "shutdown:Beta", "shutdown:Alpha", "shutdown:ScannedService", "shutdown"), RecordingExtension.getEvents(ObservedApplication.class));
    }

    @Test
    void extensionsSeeProvidedValues() {
        this.injector.initialize(new ProvidedApplication(), List.of(SettingsFactory.class, Repository.class));

        assertTrue(RecordingExtension.getEvents(ProvidedApplication.class).containsAll(List.of("create:Settings", "create:Pool")));
    }

    @Test
    void extensionCanInstantiateComponents() {
        this.injector.initialize(new TestApplication(), List.of(Made.class));

        assertEquals("extension", this.injector.get(Made.class).source());
    }

    @Test
    void diamondDependentShutsDownOnce() {
        this.injector.initialize(new BaseApplication());
        this.injector.initialize(new MiddleApplication());
        this.injector.initialize(new TopApplication());

        this.injector.shutdown(new BaseApplication());

        assertEquals(1, Collections.frequency(RecordingExtension.getEvents(TopApplication.class), "shutdown"));
        assertThrows(ApplicationNotInitializedException.class, () -> this.injector.getComponents(MiddleApplication.class));
    }

    @Test
    void dependsOnIsInheritedFromInterfaces() {
        this.injector.initialize(new TestApplication(), List.of(Gamma.class, Beta.class));

        final List<Class<?>> componentList = this.injector.getComponents(TestApplication.class);
        assertTrue(componentList.indexOf(Beta.class) < componentList.indexOf(Gamma.class));
    }

    @Test
    void shuttingDownWaitingApplicationCancelsIt() {
        this.injector.initialize(new ClansApplication(), List.of(ClanManager.class));
        this.injector.shutdown(new ClansApplication());

        this.injector.initialize(new CoreApplication(), List.of(Repository.class));

        assertThrows(ApplicationNotInitializedException.class, () -> this.injector.getComponents(ClansApplication.class));
    }

    @Application
    private static final class TestApplication {

        private final List<String> eventList = new ArrayList<>();
    }

    @Application
    private static final class AddonApplication {
    }

    @Application
    private static final class CoreApplication {
    }

    @Application(dependencies = CoreApplication.class)
    private static final class ClansApplication {
    }

    @Application(dependencies = CoreApplication.class)
    private static final class BrokenApplication implements ApplicationCallback {

        private Throwable failure;

        @Override
        public void onApplicationFailure(final Throwable throwable) {
            this.failure = throwable;
        }
    }

    @Application
    private static final class ObservedApplication {
    }

    @Application
    private static final class ProvidedApplication {
    }

    @Application
    private static final class BaseApplication {
    }

    @Application(dependencies = BaseApplication.class)
    private static final class MiddleApplication {
    }

    @Application(dependencies = {BaseApplication.class, MiddleApplication.class})
    private static final class TopApplication {
    }

    @Application
    private static final class SortedApplication implements ApplicationCallback {

        @Override
        public Comparator<Class<?>> getComponentSorter() {
            return Comparator.comparing(Class::getSimpleName, Comparator.reverseOrder());
        }
    }

    private interface Greeter {
    }

    private interface Feature {
    }

    private interface Widget {
    }

    @DependsOn(Beta.class)
    private interface NeedsBeta {
    }

    record Made(
            String source
    ) {
    }

    private record Gamma() implements NeedsBeta {
    }

    @Singleton
    private record ScannedService() {
    }

    private record Repository() {
    }

    private record Service(
            Repository repository,
            TestApplication application
    ) {
    }

    private record AuditService(
            Repository repository
    ) {
    }

    private record ClanManager(
            Repository repository
    ) {
    }

    private record EnglishGreeter() implements Greeter {
    }

    private record FrenchGreeter() implements Greeter {
    }

    private record Greeting(
            Greeter greeter
    ) {
    }

    private record Chicken(
            Egg egg
    ) {
    }

    private record Egg(
            Chicken chicken
    ) {
    }

    @DependsOn(Second.class)
    private record First(
            TestApplication application
    ) implements Lifecycle {

        @Override
        public void onComponentInitialize() {
            this.application.eventList.add("initialize:First");
        }

        @Override
        public void onComponentShutdown() {
            this.application.eventList.add("shutdown:First");
        }
    }

    private record Second(
            TestApplication application
    ) implements Lifecycle {

        @Override
        public void onComponentInitialize() {
            this.application.eventList.add("initialize:Second");
        }

        @Override
        public void onComponentShutdown() {
            this.application.eventList.add("shutdown:Second");
        }
    }

    private record Alpha() {
    }

    private record Beta() {
    }

    private record FirstFeature() implements Feature {
    }

    private record SecondFeature() implements Feature {
    }

    private record FeatureLookup(
            List<Feature> featureList,
            Set<Feature> featureSet,
            Map<Class<? extends Feature>, Feature> featureMap
    ) {
    }

    private record Dashboard(
            List<Feature> featureList
    ) implements Widget {
    }

    private record WidgetList(
            List<Widget> widgets
    ) {
    }

    private record NamedFeatureMap(
            Map<String, Feature> featureMap
    ) {
    }

    private record Settings(
            String host
    ) {
    }

    private record Connection(
            Settings settings
    ) {
    }

    private static final class Pool implements Lifecycle {

        private boolean initialized;

        @Override
        public void onComponentInitialize() {
            this.initialized = true;
        }
    }

    private record SettingsFactory() {

        @Provider
        private Settings settings(final Repository repository) {
            return new Settings("localhost");
        }

        @Provider
        private Pool pool() {
            return new Pool();
        }
    }

    private record NullFactory() {

        @Provider
        private Settings settings() {
            return null;
        }
    }

    private static final class TwoConstructors {

        private TwoConstructors() {
        }

        private TwoConstructors(final String value) {
        }
    }

    private static final class Exploding {

        private Exploding() {
            throw new IllegalStateException("Exploding on purpose");
        }
    }
}
