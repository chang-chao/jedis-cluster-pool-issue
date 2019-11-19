package me.changchao.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

import static java.util.Collections.emptyList;

public class CommonsObjectPoolMetrics implements MeterBinder, AutoCloseable {

  private final Logger logger = LoggerFactory.getLogger(CommonsObjectPoolMetrics.class);

  Executor executor = Executors.newSingleThreadExecutor();
  private static final String JMX_DOMAIN = "org.apache.commons.pool2";
  private static final String METRIC_NAME_PREFIX = "commons.pool2.";

  private final MBeanServer mBeanServer = getMBeanServer();
  private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

  private final Iterable<Tag> tags = emptyList();

  private static MBeanServer getMBeanServer() {
    List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
    if (!mBeanServers.isEmpty()) {
      return mBeanServers.get(0);
    }
    return ManagementFactory.getPlatformMBeanServer();
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    registerMetricsEventually(
        "GenericObjectPool",
        (o, tags) -> {
          registerGaugeForObject(registry, o, "MaxIdle", tags, "max idle", null);
          registerGaugeForObject(registry, o, "MinIdle", tags, "min idle", null);
          registerGaugeForObject(registry, o, "NumIdle", tags, "num idle", null);
          registerGaugeForObject(registry, o, "NumWaiters", tags, "num waiters", null);
          registerGaugeForObject(registry, o, "CreatedCount", tags, "created count", null);
          registerGaugeForObject(registry, o, "BorrowedCount", tags, "borrowed count", null);
          registerGaugeForObject(registry, o, "ReturnedCount", tags, "returned count", null);
          registerGaugeForObject(registry, o, "DestroyedCount", tags, "destroyed count", null);
          registerGaugeForObject(
              registry, o, "DestroyedByEvictorCount", tags, "destroyed by evictor count", null);
          registerGaugeForObject(
              registry,
              o,
              "DestroyedByBorrowValidationCount",
              tags,
              "destroyed by borrow validation count",
              null);
          registerGaugeForObject(
              registry, o, "MaxBorrowWaitTimeMillis", tags, "max borrow wait time", "milliseconds");
          registerGaugeForObject(
              registry, o, "MeanActiveTimeMillis", tags, "mean active time", "milliseconds");
          registerGaugeForObject(
              registry, o, "MeanIdleTimeMillis", tags, "mean idle time", "milliseconds");
          registerGaugeForObject(
              registry,
              o,
              "MeanBorrowWaitTimeMillis",
              tags,
              "mean borrow wait time",
              "milliseconds");
        });
  }

  private Iterable<Tag> nameTag(ObjectName name)
      throws AttributeNotFoundException, MBeanException, ReflectionException,
          InstanceNotFoundException {
    String factoryType = mBeanServer.getAttribute(name, "FactoryType").toString();
    Tags tags = Tags.of("name", name.getKeyProperty("name"), "factoryType", factoryType);
    return tags;
  }

  private void registerMetricsEventually(String type, BiConsumer<ObjectName, Tags> perObject) {
    try {
      Set<ObjectName> objs =
          mBeanServer.queryNames(new ObjectName(JMX_DOMAIN + ":type=" + type + ",*"), null);
      if (!objs.isEmpty()) {
        for (ObjectName o : objs) {
          Iterable<Tag> nameTags = emptyList();
          try {
            nameTags = nameTag(o);
          } catch (Exception e) {
            logger.error("exception in determining name tag", e);
          }
          perObject.accept(o, Tags.concat(tags, nameTags));
        }
        return;
      }
    } catch (MalformedObjectNameException e) {
      throw new RuntimeException("Error registering Kafka JMX based metrics", e);
    }

    registerNotificationListener(type, perObject);
  }

  /**
   * This notification listener should remain indefinitely since new Kafka consumers can be added at
   * any time.
   *
   * @param type The Kafka JMX type to listen for.
   * @param perObject Metric registration handler when a new MBean is created.
   */
  private void registerNotificationListener(String type, BiConsumer<ObjectName, Tags> perObject) {
    NotificationListener notificationListener =
        (notification, handback) -> {
          executor.execute(
              () -> {
                MBeanServerNotification mbs = (MBeanServerNotification) notification;
                ObjectName o = mbs.getMBeanName();
                mbs.getUserData();
                Iterable<Tag> nameTags = emptyList();
                int maxTries = 3;
                for (int i = 0; i < maxTries; i++) {
                  try {
                    Thread.sleep(1 * 1000);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  try {
                    nameTags = nameTag(o);
                    break;
                  } catch (AttributeNotFoundException
                      | MBeanException
                      | ReflectionException
                      | InstanceNotFoundException e) {
                    if (i == maxTries - 1) {
                      logger.error("can not set name tag", e);
                    }
                    continue;
                  }
                }
                perObject.accept(o, Tags.concat(tags, nameTags));
              });
        };

    NotificationFilter filter =
        (NotificationFilter)
            notification -> {
              if (!MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType()))
                return false;
              ObjectName obj = ((MBeanServerNotification) notification).getMBeanName();
              return obj.getDomain().equals(JMX_DOMAIN) && obj.getKeyProperty("type").equals(type);
            };

    try {
      mBeanServer.addNotificationListener(
          MBeanServerDelegate.DELEGATE_NAME, notificationListener, filter, null);
      notificationListenerCleanUpRunnables.add(
          () -> {
            try {
              mBeanServer.removeNotificationListener(
                  MBeanServerDelegate.DELEGATE_NAME, notificationListener);
            } catch (InstanceNotFoundException | ListenerNotFoundException ignored) {
            }
          });
    } catch (InstanceNotFoundException e) {
      throw new RuntimeException("Error registering Kafka MBean listener", e);
    }
  }

  @Override
  public void close() {
    notificationListenerCleanUpRunnables.forEach(Runnable::run);
  }

  private void registerGaugeForObject(
      MeterRegistry registry,
      ObjectName o,
      String jmxMetricName,
      String meterName,
      Tags allTags,
      String description,
      @Nullable String baseUnit) {
    final AtomicReference<Gauge> gauge = new AtomicReference<>();
    gauge.set(
        Gauge.builder(
                METRIC_NAME_PREFIX + meterName,
                mBeanServer,
                getJmxAttribute(registry, gauge, o, jmxMetricName))
            .description(description)
            .baseUnit(baseUnit)
            .tags(allTags)
            .register(registry));
  }

  private static String sanitize(String value) {
    String s = value.replaceAll("-", ".");
    return s.substring(0, 1).toLowerCase() + s.substring(1);
  }

  private void registerGaugeForObject(
      MeterRegistry registry,
      ObjectName o,
      String jmxMetricName,
      Tags allTags,
      String description,
      @Nullable String baseUnit) {
    registerGaugeForObject(
        registry, o, jmxMetricName, sanitize(jmxMetricName), allTags, description, baseUnit);
  }

  private ToDoubleFunction<MBeanServer> getJmxAttribute(
      MeterRegistry registry,
      AtomicReference<? extends Meter> meter,
      ObjectName o,
      String jmxMetricName) {
    return s ->
        safeDouble(
            jmxMetricName,
            () -> {
              if (!s.isRegistered(o)) {
                registry.remove(meter.get());
              }
              return s.getAttribute(o, jmxMetricName);
            });
  }

  private double safeDouble(String jmxMetricName, Callable<Object> callable) {
    try {
      return Double.parseDouble(callable.call().toString());
    } catch (Exception e) {
      return Double.NaN;
    }
  }
}
