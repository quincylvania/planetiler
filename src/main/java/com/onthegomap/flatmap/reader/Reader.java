package com.onthegomap.flatmap.reader;

import com.onthegomap.flatmap.FeatureRenderer;
import com.onthegomap.flatmap.FlatMapConfig;
import com.onthegomap.flatmap.ProgressLoggers;
import com.onthegomap.flatmap.RenderableFeature;
import com.onthegomap.flatmap.RenderableFeatures;
import com.onthegomap.flatmap.RenderedFeature;
import com.onthegomap.flatmap.SourceFeature;
import com.onthegomap.flatmap.collections.MergeSortFeatureMap;
import com.onthegomap.flatmap.profiles.OpenMapTilesProfile;
import com.onthegomap.flatmap.stats.Stats;
import com.onthegomap.flatmap.worker.Topology;
import com.onthegomap.flatmap.worker.Topology.SourceStep;
import java.util.concurrent.atomic.AtomicLong;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Reader {

  private final Stats stats;
  private final Logger LOGGER = LoggerFactory.getLogger(getClass());

  public Reader(Stats stats) {
    this.stats = stats;
  }

  public final void process(String name, FeatureRenderer renderer, MergeSortFeatureMap writer, FlatMapConfig config) {
    long featureCount = getCount();
    int threads = config.threads();
    Envelope env = config.envelope();
    OpenMapTilesProfile profile = config.profile();
    AtomicLong featuresRead = new AtomicLong(0);
    AtomicLong featuresWritten = new AtomicLong(0);

    var topology = Topology.fromGenerator(name + "_read", stats, open())
      .addBuffer(name + "_reader", 1000)
      .<RenderedFeature>addWorker(name + "_process", threads, (prev, next) -> {
        RenderableFeatures features = new RenderableFeatures();
        SourceFeature sourceFeature;
        while ((sourceFeature = prev.get()) != null) {
          featuresRead.incrementAndGet();
          features.reset(sourceFeature);
          if (sourceFeature.getGeometry().getEnvelopeInternal().intersects(env)) {
            profile.processFeature(sourceFeature, features);
            for (RenderableFeature renderable : features.all()) {
              renderer.renderFeature(renderable, next);
            }
          }
        }
      })
      .addBuffer(name + "_writer", 1000)
      .sinkToConsumer(name + "_write", 1, (item) -> {
        featuresWritten.incrementAndGet();
        writer.accept(item);
      });

    var loggers = new ProgressLoggers(name)
      .addRatePercentCounter("read", featureCount, featuresRead)
      .addRateCounter("write", featuresWritten)
      .addFileSize(writer::getStorageSize)
      .addProcessStats()
      .addTopologyStats(topology);

    topology.awaitAndLog(loggers, config.logIntervalSeconds());
  }

  public abstract long getCount();

  public abstract SourceStep<SourceFeature> open();

}