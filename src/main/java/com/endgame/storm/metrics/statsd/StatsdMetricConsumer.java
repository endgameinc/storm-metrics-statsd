/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *  Copyright 2013 Endgame Inc.
 *
 */

package com.endgame.storm.metrics.statsd;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.storm.Config;
import org.apache.storm.metric.api.IMetricsConsumer;
import org.apache.storm.task.IErrorReporter;
import org.apache.storm.task.TopologyContext;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

/**
 * @author Jason Trost
 */
public class StatsdMetricConsumer implements IMetricsConsumer {

	public static final Logger LOG = LoggerFactory.getLogger(StatsdMetricConsumer.class);

	public static final String STATSD_HOST = "metrics.statsd.host";
	public static final String STATSD_PORT = "metrics.statsd.port";
	public static final String STATSD_PREFIX = "metrics.statsd.prefix";

	String topologyName;
	String statsdHost;
	int statsdPort = 8125;
	String statsdPrefix = "storm.metrics.";

	transient StatsDClient statsd;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, Object registrationArgument,
			TopologyContext context, IErrorReporter errorReporter) {
		parseConfig(stormConf);

		if (registrationArgument instanceof Map) {
			parseConfig((Map) registrationArgument);
		}

		statsd = new NonBlockingStatsDClient(statsdPrefix + clean(topologyName), statsdHost, statsdPort);
	}

	void parseConfig(@SuppressWarnings("rawtypes") Map conf) {
		if (conf.containsKey(Config.TOPOLOGY_NAME)) {
			topologyName = (String) conf.get(Config.TOPOLOGY_NAME);
		}

		if (conf.containsKey(STATSD_HOST)) {
			statsdHost = (String) conf.get(STATSD_HOST);
		}

		if (conf.containsKey(STATSD_PORT)) {
			statsdPort = ((Number) conf.get(STATSD_PORT)).intValue();
		}

		if (conf.containsKey(STATSD_PREFIX)) {
			statsdPrefix = (String) conf.get(STATSD_PREFIX);
			if (!statsdPrefix.endsWith(".")) {
				statsdPrefix += ".";
			}
		}
	}

	String clean(String s) {
		return s.replace('.', '_').replace('/', '_');
	}

	@Override
	public void handleDataPoints(TaskInfo taskInfo,
			Collection<DataPoint> dataPoints) {
		for (Metric metric : dataPointsToMetrics(taskInfo, dataPoints)) {
			report(metric.name, metric.value);
		}
	}

	public static class Metric {
		String name;
		int value;

		public Metric(String name, int value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Metric other = (Metric) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (value != other.value)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Metric [name=" + name + ", value=" + value + "]";
		}
	}

	List<Metric> dataPointsToMetrics(TaskInfo taskInfo,
			Collection<DataPoint> dataPoints) {
		List<Metric> res = new LinkedList<>();

		StringBuilder sb = new StringBuilder()
				.append(clean(taskInfo.srcWorkerHost)).append(".")
				.append(taskInfo.srcWorkerPort).append(".")
				.append(clean(taskInfo.srcComponentId)).append(".");

		int hdrLength = sb.length();

		for (DataPoint p : dataPoints) {

			sb.delete(hdrLength, sb.length());
			sb.append(clean(p.name));

			if (p.value instanceof Number) {
				res.add(new Metric(sb.toString(), ((Number) p.value).intValue()));
			} else if (p.value instanceof Map) {
				int hdrAndNameLength = sb.length();
				@SuppressWarnings("rawtypes")
				Map map = (Map) p.value;
				for (Object subName : map.keySet()) {
					Object subValue = map.get(subName);
					if (subValue instanceof Number) {
						sb.delete(hdrAndNameLength, sb.length());
						sb.append(".").append(clean(subName.toString()));

						res.add(new Metric(sb.toString(), ((Number) subValue).intValue()));
					}
				}
			}
		}
		return res;
	}

	public void report(String s, int number) {
		LOG.debug("reporting: {}={}", s, number);
		statsd.count(s, number);
	}

	@Override
	public void cleanup() {
		statsd.stop();
	}
}
