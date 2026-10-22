package me.trae.foundation.injector.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.trae.foundation.injector.api.annotation.Application;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LiveCollectionTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void emptyInjectedCollectionsRejectMutationEvenWhenNothingWouldChange() {
        this.injector.initialize(new TestApplication(), List.of(FeatureLookup.class));

        final FeatureLookup featureLookup = this.injector.get(FeatureLookup.class);
        final List<Feature> featureList = featureLookup.getFeatureList();
        assertThrows(UnsupportedOperationException.class, featureList::clear);
        assertThrows(UnsupportedOperationException.class, () -> featureList.addAll(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> featureList.removeIf((final Feature _) -> false));
        assertThrows(UnsupportedOperationException.class, () -> featureList.replaceAll((final Feature feature) -> feature));
        assertThrows(UnsupportedOperationException.class, () -> featureList.subList(0, 0).clear());
        assertThrows(UnsupportedOperationException.class, () -> featureList.reversed().clear());

        final Set<Feature> featureSet = featureLookup.getFeatureSet();
        assertThrows(UnsupportedOperationException.class, featureSet::clear);
        assertThrows(UnsupportedOperationException.class, () -> featureSet.addAll(Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> featureSet.removeAll(Set.of()));

        final Map<Class<? extends Feature>, Feature> featureMap = featureLookup.getFeatureMap();
        assertThrows(UnsupportedOperationException.class, featureMap::clear);
        assertThrows(UnsupportedOperationException.class, () -> featureMap.remove(FirstFeature.class));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.putAll(Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.entrySet().clear());
        assertThrows(UnsupportedOperationException.class, () -> featureMap.keySet().clear());
        assertThrows(UnsupportedOperationException.class, () -> featureMap.values().clear());
    }

    @Test
    void populatedInjectedCollectionsRejectMutationThroughEntriesAndViews() {
        this.injector.initialize(new TestApplication(), List.of(FeatureLookup.class, FirstFeature.class));

        final FeatureLookup featureLookup = this.injector.get(FeatureLookup.class);
        final List<Feature> featureList = featureLookup.getFeatureList();
        final FirstFeature firstFeature = this.injector.get(FirstFeature.class);
        assertThrows(UnsupportedOperationException.class, () -> featureList.add(firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureList.set(0, firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureList.remove(firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureList.listIterator().add(firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureList.reversed().removeFirst());

        final Iterator<Feature> featureIterator = featureList.iterator();
        featureIterator.next();
        assertThrows(UnsupportedOperationException.class, featureIterator::remove);

        final Set<Feature> featureSet = featureLookup.getFeatureSet();
        assertThrows(UnsupportedOperationException.class, () -> featureSet.add(firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureSet.remove(firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureSet.removeIf((final Feature _) -> true));

        final Map<Class<? extends Feature>, Feature> featureMap = featureLookup.getFeatureMap();
        assertThrows(UnsupportedOperationException.class, () -> featureMap.put(FirstFeature.class, firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.remove(FirstFeature.class));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.replace(FirstFeature.class, firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.computeIfAbsent(
                FirstFeature.class,
                (final Class<? extends Feature> _) -> firstFeature
        ));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.replaceAll(
                (final Class<? extends Feature> _, final Feature _) -> firstFeature
        ));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.keySet().remove(FirstFeature.class));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.values().remove(firstFeature));
        assertThrows(UnsupportedOperationException.class, () -> featureMap.entrySet().iterator().next().setValue(firstFeature));

        final Iterator<Map.Entry<Class<? extends Feature>, Feature>> mapEntryIterator = featureMap.entrySet().iterator();
        mapEntryIterator.next();
        assertThrows(UnsupportedOperationException.class, mapEntryIterator::remove);

        assertSame(firstFeature, featureList.getFirst());
        assertEquals(1, featureSet.size());
        assertSame(firstFeature, featureMap.get(FirstFeature.class));
    }

    @Test
    void retainedCollectionsAndMapViewsFollowRegistrationChanges() {
        this.injector.initialize(new TestApplication(), List.of(FeatureLookup.class, FirstFeature.class));

        final FeatureLookup featureLookup = this.injector.get(FeatureLookup.class);
        final Map<Class<? extends Feature>, Feature> featureMap = featureLookup.getFeatureMap();
        final Set<Map.Entry<Class<? extends Feature>, Feature>> mapEntrySet = featureMap.entrySet();
        assertEquals(1, mapEntrySet.size());

        final Set<Class<? extends Feature>> featureClassSet = featureMap.keySet();
        assertEquals(Set.of(FirstFeature.class), featureClassSet);

        final Collection<Feature> featureCollection = featureMap.values();
        assertEquals(1, featureCollection.size());

        final Iterator<Feature> featureIterator = featureLookup.getFeatureList().iterator();
        this.injector.initialize(new AddonApplication(), List.of(SecondFeature.class));

        assertEquals(2, featureLookup.getFeatureList().size());
        assertEquals(2, featureLookup.getFeatureSet().size());
        assertEquals(2, featureMap.size());
        assertEquals(2, mapEntrySet.size());
        assertTrue(mapEntrySet.contains(Map.entry(SecondFeature.class, this.injector.get(SecondFeature.class))));
        assertEquals(Set.of(FirstFeature.class, SecondFeature.class), featureClassSet);
        assertTrue(featureCollection.contains(this.injector.get(SecondFeature.class)));
        assertSame(this.injector.get(FirstFeature.class), featureIterator.next());
        assertFalse(featureIterator.hasNext());

        this.injector.shutdown(new AddonApplication());

        assertEquals(1, featureLookup.getFeatureList().size());
        assertEquals(1, featureLookup.getFeatureSet().size());
        assertEquals(1, featureMap.size());
        assertEquals(1, mapEntrySet.size());
        assertEquals(Set.of(FirstFeature.class), featureClassSet);
        assertEquals(1, featureCollection.size());
    }

    @AllArgsConstructor
    @Getter
    private static final class FeatureLookup {

        private final List<Feature> featureList;

        private final Set<Feature> featureSet;

        private final Map<Class<? extends Feature>, Feature> featureMap;
    }

    private interface Feature {

    }

    @NoArgsConstructor
    private static final class FirstFeature implements Feature {

    }

    @NoArgsConstructor
    private static final class SecondFeature implements Feature {

    }

    @NoArgsConstructor
    @Application
    private static final class TestApplication {

    }

    @NoArgsConstructor
    @Application
    private static final class AddonApplication {

    }
}