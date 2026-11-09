package me.trae.foundation.utilities.builder;

import java.util.Map;

public interface JsonMapBuilder<Key, Value> {

    Map<Key, Value> toJsonMap();
}