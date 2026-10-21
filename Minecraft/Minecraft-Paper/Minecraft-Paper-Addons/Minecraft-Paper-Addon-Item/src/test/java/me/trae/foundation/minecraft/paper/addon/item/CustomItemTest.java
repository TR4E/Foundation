package me.trae.foundation.minecraft.paper.addon.item;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomItemTest {

    private static final NamespacedKey DYNAMIC_LORE_KEY = new NamespacedKey("custom", "item_dynamic_lore");

    @Test
    void rendersStandalonePlaceholdersWithoutChangingTheTemplate() {
        final List<String> loreList = List.of("<dynamic:owner>", "<dynamic:created_at>");
        final CustomItem customItem = this.createCustomItem(loreList);
        final ItemMeta itemMeta = this.createItemMeta();
        customItem.setDynamicLoreValue(itemMeta, "owner", "Owner: Trae");
        customItem.setDynamicLoreValue(itemMeta, "created_at", "Created At: 5mins ago");

        assertEquals(List.of("Owner: Trae", "Created At: 5mins ago"), this.renderLore(customItem, itemMeta));
        assertEquals(loreList, customItem.getLore());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Once held by: <dynamic:owner>!",
            "Owner: <dynamic:owner>?",
            "Owner: <dynamic:previous_owner>!",
            "Owner: <dynamic:owner>Trae!",
            "Owner: Trae!",
            ""
    })
    void versionsStaticTextAndSectionStructure(final String lore) {
        assertNotEquals(this.getVersion("Owner: <dynamic:owner>!"), this.getVersion(lore));
    }

    @Test
    void rendersMultipleSectionsAndSharesValuesForRepeatedKeys() {
        final CustomItem customItem = this.createCustomItem(List.of(
                "Owner: <dynamic:owner> / <dynamic:kills> kills",
                "Again: <dynamic:owner>"
        ));

        final ItemMeta itemMeta = this.createItemMeta();
        customItem.setDynamicLoreValue(itemMeta, "owner", "Trae");
        customItem.setDynamicLoreValue(itemMeta, "kills", "10");

        assertEquals(List.of("Owner: Trae / 10 kills", "Again: Trae"), this.renderLore(customItem, itemMeta));
        assertEquals(2, this.getDynamicPersistentDataContainer(itemMeta).getKeys().size());
    }

    @Test
    void preservesValuesWhenLabelsAndLineOrderChange() {
        final CustomItem originalCustomItem = this.createCustomItem(List.of("Owner: <dynamic:owner>", "Kills: <dynamic:kills>"));

        final ItemMeta itemMeta = this.createItemMeta();
        originalCustomItem.setDynamicLoreValue(itemMeta, "owner", "Trae");
        originalCustomItem.setDynamicLoreValue(itemMeta, "kills", "10");

        final CustomItem customItem = this.createCustomItem(List.of(
                "<dynamic:kills> victories",
                "Once held by: <dynamic:owner>",
                "New: <dynamic:new_key>"
        ));

        assertEquals(List.of("10 victories", "Once held by: Trae", "New: "), this.renderLore(customItem, itemMeta));
    }

    @Test
    void deletesRemovedValuesWithoutShiftingTheOthers() {
        final CustomItem customItem = this.createCustomItem(List.of("Owner: <dynamic:owner>", "Kills: <dynamic:kills>"));

        final ItemMeta itemMeta = this.createItemMeta();
        customItem.setDynamicLoreValue(itemMeta, "owner", "Trae");
        customItem.setDynamicLoreValue(itemMeta, "kills", "10");

        assertEquals(List.of("Kills: 10"), this.renderLore(this.createCustomItem(List.of("Kills: <dynamic:kills>")), itemMeta));
        assertFalse(this.getDynamicPersistentDataContainer(itemMeta).has(new NamespacedKey("dynamic", "owner")));
        assertEquals(1, this.getDynamicPersistentDataContainer(itemMeta).getKeys().size());

        assertEquals(List.of("Static lore"), this.renderLore(this.createCustomItem(List.of("Static lore")), itemMeta));
        assertFalse(itemMeta.getPersistentDataContainer().has(DYNAMIC_LORE_KEY));

        assertEquals(List.of("Owner: "), this.renderLore(this.createCustomItem(List.of("Owner: <dynamic:owner>")), itemMeta));
    }

    @Test
    void keepsValuesSpecificToEachItem() {
        final CustomItem originalCustomItem = this.createCustomItem(List.of("<dynamic:owner>"));

        final ItemMeta firstItemMeta = this.createItemMeta();
        final ItemMeta secondItemMeta = this.createItemMeta();
        originalCustomItem.setDynamicLoreValue(firstItemMeta, "owner", "Trae");
        originalCustomItem.setDynamicLoreValue(secondItemMeta, "owner", "Someone else");

        final CustomItem customItem = this.createCustomItem(List.of("Owned by <dynamic:owner>"));

        assertEquals(List.of("Owned by Trae"), this.renderLore(customItem, firstItemMeta));
        assertEquals(List.of("Owned by Someone else"), this.renderLore(customItem, secondItemMeta));
    }

    @Test
    void preservesEmptyAndReplacementSensitiveValues() {
        final CustomItem customItem = this.createCustomItem(List.of("<dynamic:price> / <dynamic:empty>"));

        final ItemMeta itemMeta = this.createItemMeta();
        customItem.setDynamicLoreValue(itemMeta, "price", "$5\\path");
        customItem.setDynamicLoreValue(itemMeta, "empty", "");

        assertEquals(
                List.of("Price: $5\\path / Empty: "),
                this.renderLore(this.createCustomItem(List.of("Price: <dynamic:price> / Empty: <dynamic:empty>")), itemMeta)
        );
        assertTrue(this.getDynamicPersistentDataContainer(itemMeta).has(new NamespacedKey("dynamic", "empty")));
    }

    @Test
    void setterRendersAndPersistsTheValueWithoutChangingVersion() {
        final CustomItem customItem = this.createCustomItem(List.of("Owner: <dynamic:owner>"));

        final ItemMeta itemMeta = this.createItemMeta();
        customItem.stamp(itemMeta);

        final String version = itemMeta.getPersistentDataContainer().get(CustomItem.VERSION_KEY, PersistentDataType.STRING);
        customItem.setDynamicLoreValue(itemMeta, "owner", "Trae$\\path");

        assertEquals("Owner: Trae$\\path", PlainTextComponentSerializer.plainText().serialize(itemMeta.lore().getFirst()));
        assertEquals(
                "Trae$\\path",
                this.getDynamicPersistentDataContainer(itemMeta).get(new NamespacedKey("dynamic", "owner"), PersistentDataType.STRING)
        );
        assertEquals(version, itemMeta.getPersistentDataContainer().get(CustomItem.VERSION_KEY, PersistentDataType.STRING));
        assertEquals(this.getVersion("Owner: <dynamic:owner>"), version);
    }

    @Test
    void rendersUnsetPlaceholdersAsEmptyWithoutStoringValues() {
        final ItemMeta itemMeta = this.createItemMeta();

        assertEquals(
                List.of("", "Owner: "),
                this.renderLore(this.createCustomItem(List.of("<dynamic:owner>", "Owner: <dynamic:owner>")), itemMeta)
        );
        assertFalse(itemMeta.getPersistentDataContainer().has(DYNAMIC_LORE_KEY));
    }

    @Test
    void rejectsValuesForSectionsMissingFromTheTemplate() {
        final CustomItem customItem = this.createCustomItem(List.of("Static lore"));

        final ItemMeta itemMeta = this.createItemMeta();
        assertThrows(IllegalArgumentException.class, () -> customItem.setDynamicLoreValue(itemMeta, "owner", "Trae"));
        assertTrue(itemMeta.getPersistentDataContainer().isEmpty());
    }

    private String getVersion(final String lore) {
        final ItemMeta itemMeta = this.createItemMeta();
        this.createCustomItem(List.of(lore)).stamp(itemMeta);

        return itemMeta.getPersistentDataContainer().get(CustomItem.VERSION_KEY, PersistentDataType.STRING);
    }

    private List<String> renderLore(final CustomItem customItem, final ItemMeta itemMeta) {
        customItem.applyLore(itemMeta);

        return itemMeta.lore().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    private CustomItem createCustomItem(final List<String> loreList) {
        return new CustomItem(Material.STONE, "test", "test") {

            @Override
            public String getName() {
                return "Test item";
            }

            @Override
            public List<String> getLore() {
                return loreList;
            }

            @Override
            protected int getMaxStackSize() {
                return 64;
            }
        };
    }

    private PersistentDataContainer getDynamicPersistentDataContainer(final ItemMeta itemMeta) {
        return itemMeta.getPersistentDataContainer().get(DYNAMIC_LORE_KEY, PersistentDataType.TAG_CONTAINER);
    }

    private ItemMeta createItemMeta() {
        final PersistentDataContainer persistentDataContainer = this.createPersistentDataContainer();
        final Map<String, Object> valueMap = new HashMap<>();

        return ItemMeta.class.cast(Proxy.newProxyInstance(
                ItemMeta.class.getClassLoader(),
                new Class<?>[]{ItemMeta.class},
                (final Object _, final Method method, final Object[] arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> persistentDataContainer;
                    case "lore" -> {
                        if (arguments == null) {
                            yield valueMap.get("lore");
                        }

                        valueMap.put("lore", arguments[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        ));
    }

    private PersistentDataContainer createPersistentDataContainer() {
        final Map<NamespacedKey, Object> valueMap = new HashMap<>();

        return PersistentDataContainer.class.cast(Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (final Object _, final Method method, final Object[] arguments) -> switch (method.getName()) {
                    case "get" -> valueMap.get(arguments[0]);
                    case "getOrDefault" -> valueMap.getOrDefault(arguments[0], arguments[2]);
                    case "getKeys" -> valueMap.keySet();
                    case "isEmpty" -> valueMap.isEmpty();
                    case "has" -> valueMap.containsKey(arguments[0]);
                    case "getAdapterContext" -> (PersistentDataAdapterContext) this::createPersistentDataContainer;
                    case "set" -> {
                        valueMap.put(NamespacedKey.class.cast(arguments[0]), arguments[2]);
                        yield null;
                    }
                    case "remove" -> {
                        valueMap.remove(arguments[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        ));
    }
}