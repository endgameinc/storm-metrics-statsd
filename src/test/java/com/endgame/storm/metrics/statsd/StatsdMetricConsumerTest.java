package com.endgame.storm.metrics.statsd;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import backtype.storm.Config;
import backtype.storm.metric.api.IMetricsConsumer.DataPoint;
import backtype.storm.metric.api.IMetricsConsumer.TaskInfo;

import com.endgame.storm.metrics.statsd.StatsdMetricConsumer.Metric;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * @author Jason Trost
 */
public class StatsdMetricConsumerTest extends TestCase {

	StatsdMetricConsumer undertest;
	
	@Override
	protected void setUp() throws Exception {
		undertest = new StatsdMetricConsumer();
	}
	
	public void testParseConfig() {
		assertNull(undertest.statsdHost);
		assertEquals("storm.metrics.", undertest.statsdPrefix);
		assertEquals(8125, undertest.statsdPort);
		assertNull(undertest.topologyName);
		
		Map conf = new HashMap();
		conf.put(StatsdMetricConsumer.STATSD_HOST, "localhost");
		conf.put(StatsdMetricConsumer.STATSD_PORT, 5555);
		conf.put(StatsdMetricConsumer.STATSD_PREFIX, "my.statsd.prefix");
		conf.put(Config.TOPOLOGY_NAME, "myTopologyName");
		
		undertest.parseConfig(conf);
		
		assertEquals("localhost", undertest.statsdHost);
		assertEquals("my.statsd.prefix.", undertest.statsdPrefix);
		assertEquals(5555, undertest.statsdPort);
		assertEquals("myTopologyName", undertest.topologyName);
	}
	
	public void testCleanString()
	{
		assertEquals("test", undertest.clean("test"));
		assertEquals("test_name", undertest.clean("test/name"));
		assertEquals("test_name", undertest.clean("test.name"));
	}
	
	public void testPrepare()
	{
		assertNull(undertest.statsdHost);
		assertEquals("storm.metrics.", undertest.statsdPrefix);
		assertEquals(8125, undertest.statsdPort);
		assertNull(undertest.topologyName);
		assertNull(undertest.statsd);
		
		Map stormConf = new HashMap();
		stormConf.put(Config.TOPOLOGY_NAME, "myTopologyName");
		
		Map registrationArgument = new HashMap();
		registrationArgument.put(StatsdMetricConsumer.STATSD_HOST, "localhost");
		registrationArgument.put(StatsdMetricConsumer.STATSD_PORT, 5555);
		registrationArgument.put(StatsdMetricConsumer.STATSD_PREFIX, "my.statsd.prefix");
		
		undertest.prepare(stormConf, registrationArgument, null, null);
		
		assertEquals("localhost", undertest.statsdHost);
		assertEquals("my.statsd.prefix.", undertest.statsdPrefix);
		assertEquals(5555, undertest.statsdPort);
		assertEquals("myTopologyName", undertest.topologyName);
		assertNotNull(undertest.statsd);
	}
	
	public void testDataPointsToMetrics()
	{
		TaskInfo taskInfo = new TaskInfo("host1", 6701, "myBolt7", 12, 123456789000L, 60);
		List<DataPoint> dataPoints = new LinkedList<>();
		
		dataPoints.add(new DataPoint("my.int", 57));
		dataPoints.add(new DataPoint("my.long", 57L));
		dataPoints.add(new DataPoint("my/float", 222f));
		dataPoints.add(new DataPoint("my_double", 56.0d));
		dataPoints.add(new DataPoint("ignored", "not a num"));
		dataPoints.add(new DataPoint("points", 
			ImmutableMap.<String, Object>of("count", 123, "time", 2342234, "ignored", "not a num")));
		
		undertest.topologyName = "testTop";
		undertest.statsdPrefix = "testPrefix";
		
		// topology and prefix are used when creating statsd, and statsd client handles adding them
		// they should not show up here
		
		List<Metric> expected = ImmutableList.<Metric>of(
			new Metric("host1.6701.myBolt7.my_int", 57),
			new Metric("host1.6701.myBolt7.my_long", 57),
			new Metric("host1.6701.myBolt7.my_float", 222),
			new Metric("host1.6701.myBolt7.my_double", 56),
			new Metric("host1.6701.myBolt7.points.count", 123),
			new Metric("host1.6701.myBolt7.points.time", 2342234)
		);

		assertEquals(expected, undertest.dataPointsToMetrics(taskInfo, dataPoints));
		
	}
}
