package com.devicehive.model.rpc;

/*
 * #%L
 * DeviceHive Common Module
 * %%
 * Copyright (C) 2016 DataArt
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.devicehive.auth.HivePrincipal;
import com.devicehive.shim.api.Action;
import com.devicehive.shim.api.Body;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.lang.reflect.Modifier;

public class CountDeviceRequest extends Body {

    private String name;
    private String namePattern;
    private Long networkId;
    private String networkName;
    private HivePrincipal principal;

    public CountDeviceRequest() {
        super(Action.COUNT_DEVICE_REQUEST);
    }

    public CountDeviceRequest(String name, String namePattern, Long networkId, String networkName, HivePrincipal principal) {
        super(Action.COUNT_DEVICE_REQUEST);
        this.name = name;
        this.namePattern = namePattern;
        this.networkId = networkId;
        this.networkName = networkName;
        this.principal = principal;
    }

    public static CountDeviceRequest createCountDeviceRequest(JsonObject request, HivePrincipal principal) {
        final CountDeviceRequest countDeviceRequest = new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.PROTECTED)
                .create()
                .fromJson(request, CountDeviceRequest.class);

        countDeviceRequest.setPrincipal(principal);

        return countDeviceRequest;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(String namePattern) {
        this.namePattern = namePattern;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public HivePrincipal getPrincipal() {
        return principal;
    }

    public void setPrincipal(HivePrincipal principal) {
        this.principal = principal;
    }
}
