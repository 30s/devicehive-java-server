package com.devicehive.resource.impl;

/*
 * #%L
 * DeviceHive Auth Logic
 * %%
 * Copyright (C) 2016 - 2017 DataArt
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


import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.resource.AuthApiInfoResource;
import com.devicehive.resource.BaseApiInfoResource;
import com.devicehive.resource.util.ResponseFactory;
import com.devicehive.service.time.TimestampService;
import com.devicehive.vo.CacheInfoVO;
import org.hibernate.SessionFactory;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Service
public class AuthApiInfoResourceImpl implements AuthApiInfoResource {

    private final BaseApiInfoResource baseApiInfoResource;

    @Autowired
    public AuthApiInfoResourceImpl(BaseApiInfoResource baseApiInfoResource) {
        this.baseApiInfoResource = baseApiInfoResource;
    }

    @Override
    public Response getApiInfo(UriInfo uriInfo, String protocol) {
        return baseApiInfoResource.getApiInfo(uriInfo, protocol);
    }
}
