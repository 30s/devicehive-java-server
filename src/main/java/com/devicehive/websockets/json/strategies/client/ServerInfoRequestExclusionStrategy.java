package com.devicehive.websockets.json.strategies.client;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: jkulagina
 * Date: 18.06.13
 * Time: 20:53
 */
public class ServerInfoRequestExclusionStrategy implements ExclusionStrategy {

    private static final Set<String> FIELDS_NAMES_TO_EXCLUDE;

    static{
        Set<String> initSet = new HashSet<>();
        initSet.add("id");
        initSet.add("status");
        initSet.add("requestId");
        initSet.add("apiVersion");
        initSet.add("serverTimestamp");
        initSet.add("restServerUrl");
        FIELDS_NAMES_TO_EXCLUDE = initSet;
    }

    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
        return FIELDS_NAMES_TO_EXCLUDE.contains(fieldAttributes.getName());
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
        return false;
    }

}