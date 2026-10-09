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
import me.trae.foundation.injector.api.exception.DuplicateComponentException;
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
    void resolvesProviderValuesByFullGenericType() {
        this.injector.initialize(new TestApplication(), List.of(GenericFactory.class, GenericConsumer.class));

        final GenericConsumer consumer = this.injector.get(GenericConsumer.class);
        assertEquals("text", consumer.text().value());
        assertEquals(42, consumer.number().value());
    }

    @Test
    void resolvesProvidersWithTwoGenericArgumentsIndependently() {
        this.injector.initialize(new TestApplication(), List.of(ComponentTypeFactory.class, ComponentTypeConsumer.class));

        final ComponentTypeConsumer consumer = this.injector.get(ComponentTypeConsumer.class);
        assertEquals("entity", consumer.combat().component().name());
        assertEquals("combat", consumer.combat().data().name());
        assertEquals("chunk", consumer.claim().component().name());
        assertEquals("claim", consumer.claim().data().name());
    }

    @Test
    void reportsAmbiguousRawAndDuplicateGenericRegistrations() {
        assertThrows(AmbiguousDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(GenericFactory.class, RawBoxConsumer.class)));
        assertThrows(DuplicateComponentException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(DuplicateGenericFactory.class)));
        assertThrows(UnsupportedDependencyTypeException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(GenericMethodFactory.class)));
        assertThrows(MissingDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(GenericFactory.class, LongBoxConsumer.class)));
    }

    @Test
    void genericLiveCollectionsAndLifecycleTrackProviderTypes() {
        GenericBox.events.clear();
        final TestApplication application = new TestApplication();
        this.injector.initialize(application, List.of(GenericLookup.class));

        final GenericLookup lookup = this.injector.get(GenericLookup.class);
        assertEquals(0, lookup.strings().size());
        assertEquals(0, lookup.stringSet().size());
        assertEquals(0, lookup.stringMap().size());

        this.injector.attach(application, List.of(GenericFactory.class));
        assertEquals(1, lookup.strings().size());
        assertEquals(1, lookup.stringSet().size());
        assertEquals(1, lookup.stringMap().size());
        assertEquals(2, GenericBox.events.size());
        assertTrue(GenericBox.events.containsAll(List.of("initialize:text", "initialize:42")));

        this.injector.detach(application, List.of(GenericBox.class));
        assertEquals(0, lookup.strings().size());
        assertEquals(0, lookup.stringSet().size());
        assertEquals(0, lookup.stringMap().size());
        assertEquals(4, GenericBox.events.size());
        assertTrue(GenericBox.events.containsAll(List.of("initialize:text", "initialize:42", "shutdown:42", "shutdown:text")));
    }

    @Test
    void wildcardGenericCollectionsFilterAndUpdateLive() {
        final TestApplication application = new TestApplication();
        this.injector.initialize(application, List.of(WildcardLookup.class));

        final WildcardLookup lookup = this.injector.get(WildcardLookup.class);
        this.injector.attach(application, List.of(WildcardItemFactory.class));

        assertEquals(7, lookup.allItems().size());
        assertEquals(3, lookup.extendedItems().size());
        assertEquals(3, lookup.superItems().size());
        assertEquals(1, lookup.loreItems().size());
        assertEquals(1, lookup.nestedItems().size());
        assertEquals(0, lookup.integerItems().size());
        assertEquals(3, lookup.extendedItemSet().size());
        assertEquals(7, lookup.allItemMap().size());

        this.injector.detach(application, List.of(WildcardItemFactory.class));
        assertEquals(0, lookup.allItems().size());
        assertEquals(0, lookup.extendedItems().size());
        assertEquals(0, lookup.superItems().size());
        assertEquals(0, lookup.loreItems().size());
        assertEquals(0, lookup.nestedItems().size());
        assertEquals(0, lookup.extendedItemSet().size());
        assertEquals(0, lookup.allItemMap().size());
    }

    @Test
    void wildcardSingleDependenciesUseBoundsAndRejectIncompatibleTypes() {
        this.injector.initialize(new TestApplication(), List.of(OnlyLoreItemFactory.class, ExtendedItemConsumer.class, ExactLoreItemConsumer.class));

        assertInstanceOf(LoreItem.class, this.injector.get(ExtendedItemConsumer.class).item());
        assertInstanceOf(LoreItem.class, this.injector.get(ExactLoreItemConsumer.class).item());
        assertThrows(MissingDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(OnlyLoreItemFactory.class, IncompatibleItemConsumer.class)));
    }

    @Test
    void resolvesGenericSuperclassAndInterfaceHierarchies() {
        this.injector.initialize(new TestApplication(), List.of(GenericHierarchyConsumer.class, StringRepository.class, StringListRepository.class));

        final GenericHierarchyConsumer consumer = this.injector.get(GenericHierarchyConsumer.class);
        assertInstanceOf(StringRepository.class, consumer.repository());
        assertInstanceOf(StringListRepository.class, consumer.nestedRepository());

        assertThrows(MissingDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(IntegerRepositoryConsumer.class, StringRepository.class)));
        assertThrows(AmbiguousDependencyException.class, () -> new CoreInjector().initialize(new TestApplication(), List.of(StringRepositoryConsumer.class, StringRepository.class, AlternativeStringRepository.class)));
    }

    @Test
    void detachingProviderOwnerRemovesOnlyOwnedValuesAndDependents() {
        GenericBox.events.clear();
        final TestApplication application = new TestApplication();
        this.injector.initialize(application, List.of(GenericLookup.class));
        final GenericLookup lookup = this.injector.get(GenericLookup.class);

        this.injector.attach(application, List.of(StringGenericFactory.class, IntegerGenericFactory.class, GenericStringConsumer.class, GenericIntegerConsumer.class));
        assertEquals(1, lookup.strings().size());
        assertEquals(1, lookup.integers().size());
        assertEquals(1, Collections.frequency(this.injector.getComponents(TestApplication.class), GenericBox.class));

        this.injector.detach(application, List.of(StringGenericFactory.class));
        assertEquals(0, lookup.strings().size());
        assertEquals(1, lookup.integers().size());
        assertThrows(MissingDependencyException.class, () -> this.injector.get(GenericStringConsumer.class));
        assertNotNull(this.injector.get(GenericIntegerConsumer.class));
        assertTrue(GenericBox.events.contains("shutdown:string-owner"));
        assertTrue(GenericBox.events.contains("initialize:7"));

        this.injector.shutdown(application);
        assertEquals(0, lookup.integers().size());
    }

    @Test
    void failedGenericAttachmentRollsBackOwnersValuesAndDependents() {
        final TestApplication application = new TestApplication();
        this.injector.initialize(application, List.of(GenericLookup.class));
        final GenericLookup lookup = this.injector.get(GenericLookup.class);

        assertThrows(ComponentCreationException.class, () -> this.injector.attach(application, List.of(StringGenericFactory.class, GenericStringConsumer.class, ZZGenericExploding.class)));
        assertEquals(0, lookup.strings().size());
        assertThrows(MissingDependencyException.class, () -> this.injector.get(GenericStringConsumer.class));

        this.injector.attach(application, List.of(StringGenericFactory.class, GenericStringConsumer.class));
        assertEquals(1, lookup.strings().size());
    }

    @Test
    void detachingAProvidedValueAlsoRemovesItsOwner() {
        final TestApplication application = new TestApplication();
        this.injector.initialize(application, List.of(GenericLookup.class));
        final GenericLookup lookup = this.injector.get(GenericLookup.class);

        this.injector.attach(application, List.of(StringGenericFactory.class));
        assertEquals(1, lookup.strings().size());
        this.injector.detach(application, List.of(GenericBox.class));
        assertEquals(0, lookup.strings().size());

        this.injector.attach(application, List.of(StringGenericFactory.class));
        assertEquals(1, lookup.strings().size());
    }

    @Test
    void applicationShutdownRemovesCrossApplicationComponentDependents() {
        final TestApplication ownerApplication = new TestApplication();
        this.injector.initialize(ownerApplication, List.of(StringGenericFactory.class));
        this.injector.initialize(new AddonApplication(), List.of(GenericStringConsumer.class));
        assertNotNull(this.injector.get(GenericStringConsumer.class));

        this.injector.shutdown(ownerApplication);

        assertThrows(MissingDependencyException.class, () -> this.injector.get(GenericStringConsumer.class));
        assertEquals(List.of(), this.injector.getComponents(AddonApplication.class));
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

        assertEquals(List.of("create:Alpha", "create:Beta", "create:ScannedService", "initialize", "shutdown:ScannedService", "shutdown:Beta", "shutdown:Alpha", "shutdown"), RecordingExtension.getEvents(ObservedApplication.class));
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

    private interface GenericRepository<T> {
    }

    private interface ReadRepository<T> extends GenericRepository<T> {
    }

    private interface NestedRepository<T> {
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

    private record GenericBox<T>(T value) implements Lifecycle {

        private static final List<String> events = new ArrayList<>();

        @Override
        public void onComponentInitialize() {
            events.add("initialize:" + this.value);
        }

        @Override
        public void onComponentShutdown() {
            events.add("shutdown:" + this.value);
        }
    }

    private record GenericConsumer(
            GenericBox<String> text,
            GenericBox<Integer> number
    ) {
    }

    private record GenericLookup(
            List<GenericBox<String>> strings,
            List<GenericBox<Integer>> integers,
            Set<GenericBox<String>> stringSet,
            Map<Class<? extends GenericBox<String>>, GenericBox<String>> stringMap
    ) {
    }

    private record WildcardLookup(
            List<CustomItem<?>> allItems,
            List<CustomItem<? extends BaseComponent>> extendedItems,
            List<CustomItem<? super LoreComponent>> superItems,
            List<CustomItem<LoreComponent>> loreItems,
            List<CustomItem<List<? extends BaseComponent>>> nestedItems,
            List<CustomItem<Integer>> integerItems,
            Set<CustomItem<? extends BaseComponent>> extendedItemSet,
            Map<Class<? extends CustomItem<?>>, CustomItem<?>> allItemMap
    ) {
    }

    private record ExtendedItemConsumer(
            CustomItem<? extends BaseComponent> item
    ) {
    }

    private record ExactLoreItemConsumer(
            CustomItem<LoreComponent> item
    ) {
    }

    private record IncompatibleItemConsumer(
            CustomItem<Integer> item
    ) {
    }

    private interface CustomItem<T> {
    }

    private abstract static class CustomItemBase<T> implements CustomItem<T> {
    }

    private abstract static class CustomItemIntermediate<U> extends CustomItemBase<U> {
    }

    private static final class LoreItem extends CustomItemIntermediate<LoreComponent> {
    }

    private static final class BaseItem extends CustomItemIntermediate<BaseComponent> {
    }

    private static final class SpecialItem extends CustomItemIntermediate<SpecialComponent> {
    }

    private static final class OtherItem extends CustomItemIntermediate<OtherComponent> {
    }

    private static final class ObjectItem extends CustomItemIntermediate<Object> {
    }

    private static final class NestedLoreItem extends CustomItemBase<List<LoreComponent>> {
    }

    private static final class NestedOtherItem extends CustomItemBase<List<OtherComponent>> {
    }

    private interface BaseComponent {
    }

    private static final class LoreComponent implements BaseComponent {
    }

    private static final class SpecialComponent implements BaseComponent {
    }

    private static final class OtherComponent {
    }

    private record WildcardItemFactory() {

        @Provider
        private LoreItem loreItem() {
            return new LoreItem();
        }

        @Provider
        private BaseItem baseItem() {
            return new BaseItem();
        }

        @Provider
        private SpecialItem specialItem() {
            return new SpecialItem();
        }

        @Provider
        private OtherItem otherItem() {
            return new OtherItem();
        }

        @Provider
        private ObjectItem objectItem() {
            return new ObjectItem();
        }

        @Provider
        private NestedLoreItem nestedLoreItem() {
            return new NestedLoreItem();
        }

        @Provider
        private NestedOtherItem nestedOtherItem() {
            return new NestedOtherItem();
        }
    }

    private record OnlyLoreItemFactory() {

        @Provider
        private LoreItem loreItem() {
            return new LoreItem();
        }
    }

    private record RawBoxConsumer(
            GenericBox box
    ) {
    }

    private record LongBoxConsumer(
            GenericBox<Long> box
    ) {
    }

    private record GenericHierarchyConsumer(
            GenericRepository<String> repository,
            NestedRepository<List<String>> nestedRepository
    ) {
    }

    private record StringRepositoryConsumer(
            GenericRepository<String> repository
    ) {
    }

    private record IntegerRepositoryConsumer(
            GenericRepository<Integer> repository
    ) {
    }

    private abstract static class RepositoryBase<T> implements ReadRepository<T> {
    }

    private abstract static class RepositoryIntermediate<U> extends RepositoryBase<U> {
    }

    private static final class StringRepository extends RepositoryIntermediate<String> {
    }

    private static final class AlternativeStringRepository extends RepositoryIntermediate<String> {
    }

    private abstract static class NestedRepositoryBase<T> implements NestedRepository<T> {
    }

    private abstract static class NestedRepositoryIntermediate<U> extends NestedRepositoryBase<List<U>> {
    }

    private static final class StringListRepository extends NestedRepositoryIntermediate<String> {
    }

    private record GenericStringConsumer(
            GenericBox<String> box
    ) {
    }

    private record GenericIntegerConsumer(
            GenericBox<Integer> box
    ) {
    }

    private record StringGenericFactory() {

        @Provider
        private GenericBox<String> stringBox() {
            return new GenericBox<>("string-owner");
        }
    }

    private record IntegerGenericFactory() {

        @Provider
        private GenericBox<Integer> integerBox() {
            return new GenericBox<>(7);
        }
    }

    private static final class ZZGenericExploding {

        private ZZGenericExploding() {
            throw new IllegalStateException("Fail generic attachment on purpose");
        }
    }

    private record GenericFactory() {

        @Provider
        private GenericBox<String> text() {
            return new GenericBox<>("text");
        }

        @Provider
        private GenericBox<Integer> number() {
            return new GenericBox<>(42);
        }
    }

    private record DuplicateGenericFactory() {

        @Provider
        private GenericBox<String> first() {
            return new GenericBox<>("first");
        }

        @Provider
        private GenericBox<String> second() {
            return new GenericBox<>("second");
        }
    }

    private record GenericMethodFactory() {

        @Provider
        private <T> GenericBox<T> generic() {
            return new GenericBox<>(null);
        }
    }

    private record ComponentType<A, B>(A component, B data) {
    }

    private record ComponentTypeConsumer(
            ComponentType<EntityStore, CombatTag> combat,
            ComponentType<ChunkStore, ClaimData> claim
    ) {
    }

    private record EntityStore(String name) {
    }

    private record CombatTag(String name) {
    }

    private record ChunkStore(String name) {
    }

    private record ClaimData(String name) {
    }

    private record ComponentTypeFactory() {

        @Provider
        private ComponentType<EntityStore, CombatTag> combat() {
            return new ComponentType<>(new EntityStore("entity"), new CombatTag("combat"));
        }

        @Provider
        private ComponentType<ChunkStore, ClaimData> claim() {
            return new ComponentType<>(new ChunkStore("chunk"), new ClaimData("claim"));
        }
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