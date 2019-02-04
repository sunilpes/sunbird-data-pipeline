/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ekstep.ep.samza.task;

import org.apache.samza.config.Config;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.*;
import org.ekstep.ep.samza.core.JobMetrics;
import org.ekstep.ep.samza.core.Logger;
import org.ekstep.ep.samza.service.ContentDeNormalizationService;
import org.ekstep.ep.samza.util.*;

public class ContentDeNormalizationTask implements StreamTask, InitableTask, WindowableTask {

    static Logger LOGGER = new Logger(ContentDeNormalizationTask.class);
    private ContentDeNormalizationConfig config;
    private DeviceDataCache deviceCache;
    private UserDataCache userCache;
    private ContentDataCache contentCache;
    private RedisConnect redisConnect;
    private JobMetrics metrics;
    private ContentDeNormalizationService service;

    public ContentDeNormalizationTask(Config config, TaskContext context, DeviceDataCache deviceCache, UserDataCache userCache, ContentDataCache contentCache)  {
        init(config, context, deviceCache, userCache, contentCache);
    }

    public ContentDeNormalizationTask() {

    }

    @SuppressWarnings("unchecked")
    @Override
    public void init(Config config, TaskContext context) {
        init(config, context, deviceCache, userCache, contentCache);
    }


    public void init(Config config, TaskContext context, DeviceDataCache deviceCache, UserDataCache userCache, ContentDataCache contentCache) {
        this.config = new ContentDeNormalizationConfig(config);
        metrics = new JobMetrics(context, this.config.jobName());
        this.redisConnect = new RedisConnect(config);

        this.deviceCache =
                deviceCache == null ?
                        new DeviceDataCache(config, this.redisConnect)
                        : deviceCache;

        this.userCache =
                userCache == null ?
                        new UserDataCache(config, this.redisConnect)
                        : userCache;

        this.contentCache =
                contentCache == null ?
                        new ContentDataCache(config, this.redisConnect)
                        : contentCache;

        service = new ContentDeNormalizationService(this.config, this.deviceCache, this.redisConnect, this.userCache, this.contentCache);
    }

    @Override
    public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator taskCoordinator) {
        try {
            ContentDeNormalizationSource source = new ContentDeNormalizationSource(envelope);
            ContentDeNormalizationSink sink = new ContentDeNormalizationSink(collector, metrics, config);

            service.process(source, sink);
        } catch (Exception ex) {
            LOGGER.error("", "DeNormalization failed: " + ex.getMessage());
            Object event = envelope.getMessage();
            if (event != null) {
                LOGGER.info("", "FAILED_EVENT: " + event);
            }
        }
    }

    @Override
    public void window(MessageCollector collector, TaskCoordinator coordinator) throws Exception {
        String mEvent = metrics.collect();
        collector.send(new OutgoingMessageEnvelope(new SystemStream("kafka", config.metricsTopic()), mEvent));
        metrics.clear();
    }
}