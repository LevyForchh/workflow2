package com.liveramp.workflow_ui;

import javax.servlet.DispatcherType;
import java.net.InetAddress;
import java.net.URL;
import java.util.Date;
import java.util.EnumSet;
import java.util.Timer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.session.JDBCSessionIdManager;
import org.eclipse.jetty.server.session.JDBCSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;

import com.liveramp.workflow_db_state.ThreadLocalWorkflowDb;
import com.liveramp.workflow_ui.servlet.AlertServlet;
import com.liveramp.workflow_ui.servlet.AppCostHistoryServlet;
import com.liveramp.workflow_ui.servlet.ApplicationListServlet;
import com.liveramp.workflow_ui.servlet.ApplicationQueryServlet;
import com.liveramp.workflow_ui.servlet.AttemptStateServlet;
import com.liveramp.workflow_ui.servlet.AvailableNotificationServlet;
import com.liveramp.workflow_ui.servlet.ClusterAppAlerts;
import com.liveramp.workflow_ui.servlet.ClusterConstants;
import com.liveramp.workflow_ui.servlet.HDFSIOServlet;
import com.liveramp.workflow_ui.servlet.ClusterUsageServlet;
import com.liveramp.workflow_ui.servlet.CostServlet;
import com.liveramp.workflow_ui.servlet.ExecutionQueryServlet;
import com.liveramp.workflow_ui.servlet.JSONServlet;
import com.liveramp.workflow_ui.servlet.NameNodeUsageServlet;
import com.liveramp.workflow_ui.servlet.PipelineServlet;
import com.liveramp.workflow_ui.servlet.ShuffleIOServlet;
import com.liveramp.workflow_ui.servlet.StatServlet;
import com.liveramp.workflow_ui.servlet.TaskExceptionServlet;
import com.liveramp.workflow_ui.servlet.command.UserConfigServlet;
import com.liveramp.workflow_ui.servlet.command.AttemptCommandServlet;
import com.liveramp.workflow_ui.servlet.command.DashboardServlet;
import com.liveramp.workflow_ui.servlet.command.ExecutionCommandServlet;
import com.liveramp.workflow_ui.servlet.command.NotificationConfigurationServlet;
import com.liveramp.workflow_ui.util.dashboards.SeedDashboardTask;
import com.liveramp.workflow_ui.util.prime.Prime;
import com.liveramp.workflow_ui.util.prime.RequestBuilder;
import com.liveramp.workflow_ui.util.prime.TimeIntervalBuilder;
import com.rapleaf.jack.DatabaseConnectionConfiguration;

public class WorkflowDbWebServer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(WorkflowDbWebServer.class);

  private final Semaphore shutdownLock = new Semaphore(0);

  private final Timer taskTimer;
  ThreadLocalWorkflowDb databases = new ThreadLocalWorkflowDb();


  public WorkflowDbWebServer() {
    taskTimer = new Timer(true);
  }

  public final void shutdown() {
    taskTimer.cancel();
    shutdownLock.release();
  }

  private static final Multimap<String, RequestBuilder> getURLsToPrime() {
    Multimap<String, RequestBuilder> toPrime = HashMultimap.create();
    toPrime.put("/cluster_usage", new TimeIntervalBuilder(-30, 0));
    toPrime.put("/cluster_usage", new TimeIntervalBuilder(-7, 0));
    toPrime.put("/cluster_usage", new TimeIntervalBuilder(-37, -30));

    toPrime.put("/namenode_usage", new TimeIntervalBuilder(-30, 0));
    toPrime.put("/namenode_usage", new TimeIntervalBuilder(-7, 0));
    toPrime.put("/namenode_usage", new TimeIntervalBuilder(-37, -30));

    toPrime.put("/hdfs_io", new TimeIntervalBuilder(-30, 0));
    toPrime.put("/hdfs_io", new TimeIntervalBuilder(-7, 0));
    toPrime.put("/hdfs_io", new TimeIntervalBuilder(-37, -30));

    toPrime.put("/pipeline", new TimeIntervalBuilder(-14, 1));

    return toPrime;
  }

  @Override
  public void run() {
    try {

      //  minute after midnight
      Date firstExecution = new DateTime()
          .toLocalDate()
          .plusDays(1)
          .toDateTimeAtStartOfDay()
          .plusMinutes(1)
          .toDate();

      taskTimer.scheduleAtFixedRate(
          new Prime("127.0.0.1:8080", getURLsToPrime()),
          firstExecution,
          TimeUnit.DAYS.toMillis(1)
      );

      //  look for dashboards to auto assign to dashboards every 5 min
      int searchWindow = (int)TimeUnit.MINUTES.toMillis(5);

      taskTimer.scheduleAtFixedRate(
          new SeedDashboardTask(databases, searchWindow),
          new DateTime().plusMinutes(5).toDate(),
          searchWindow
      );

      Server uiServer = new Server(new ExecutorThreadPool(50, 50, Integer.MAX_VALUE, TimeUnit.MINUTES));

      ServerConnector http = new ServerConnector(uiServer, new HttpConnectionFactory());
      http.setPort(ClusterConstants.DEFAULT_PORT);
      http.setIdleTimeout(30000);
      uiServer.addConnector(http);

      final URL warUrl = uiServer.getClass().getClassLoader().getResource("com/liveramp/workflow_ui/www");
      final String warUrlString = warUrl.toExternalForm();

      WebAppContext context = new WebAppContext(warUrlString, "/");


      context.addServlet(new ServletHolder(new AttemptCommandServlet(databases)), "/command2");
      context.addServlet(new ServletHolder(new ExecutionCommandServlet(databases)), "/execution_command");
      context.addServlet(new ServletHolder(new NotificationConfigurationServlet(databases)), "/notification_configuration");
      context.addServlet(new ServletHolder(new DashboardServlet(databases)), "/dashboards");
      context.addServlet(new ServletHolder(new UserConfigServlet(databases)), "/user");

      context.addServlet(new ServletHolder(new JSONServlet(new AttemptStateServlet(), databases)), "/attempt_state");
      context.addServlet(new ServletHolder(new JSONServlet(new ClusterUsageServlet(), databases)), "/cluster_usage");
      context.addServlet(new ServletHolder(new JSONServlet(new NameNodeUsageServlet(), databases)), "/namenode_usage");
      context.addServlet(new ServletHolder(new JSONServlet(new HDFSIOServlet(), databases)), "/hdfs_io");
      context.addServlet(new ServletHolder(new JSONServlet(new ShuffleIOServlet(), databases)), "/shuffle_io");
      context.addServlet(new ServletHolder(new JSONServlet(new ApplicationListServlet(), databases)), "/applications");
      context.addServlet(new ServletHolder(new JSONServlet(new ApplicationQueryServlet(), databases)), "/application");
      context.addServlet(new ServletHolder(new JSONServlet(new ExecutionQueryServlet(), databases)), "/executions");
      context.addServlet(new ServletHolder(new JSONServlet(new StatServlet(), databases)), "/statistics");
      context.addServlet(new ServletHolder(new JSONServlet(new CostServlet(), databases)), "/cost");
      context.addServlet(new ServletHolder(new JSONServlet(new AvailableNotificationServlet(), databases)), "/available_notifications");
      context.addServlet(new ServletHolder(new JSONServlet(new PipelineServlet(), databases)), "/pipeline");
      context.addServlet(new ServletHolder(new JSONServlet(new TaskExceptionServlet(), databases)), "/tasks");
      context.addServlet(new ServletHolder(new JSONServlet(new AppCostHistoryServlet(), databases)), "/app_cost_history");
      context.addServlet(new ServletHolder(new JSONServlet(new AlertServlet(), databases)), "/alerts");
      context.addServlet(new ServletHolder(new JSONServlet(new ClusterAppAlerts(), databases)), "/app_alerts");

      AnnotationConfigWebApplicationContext annotation = new AnnotationConfigWebApplicationContext();
      annotation.setConfigLocation("com.liveramp.workflow_ui.security");

      context.addServlet(new ServletHolder(new org.springframework.web.servlet.DispatcherServlet(annotation)), "/*");
      context.addEventListener(new ContextLoaderListener(annotation));

      context.addFilter(GzipFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
      context.addFilter(new FilterHolder(new DelegatingFilterProxy(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)), "/*", EnumSet.allOf(DispatcherType.class));

      DatabaseConnectionConfiguration connInfo = DatabaseConnectionConfiguration.loadFromEnvironment("workflow_ui_jetty_db");

      JDBCSessionIdManager idMgr = new JDBCSessionIdManager(uiServer);
      String hostname = InetAddress.getLocalHost().getHostName().split("\\.")[0];
      LOG.info("Using hostname: "+hostname);
      
      idMgr.setWorkerName(hostname);
      idMgr.setDatasource(new DriverManagerDataSource(buildConnectURL(connInfo),
          connInfo.getUsername().get(),
          connInfo.getPassword().orNull()
      ));

      uiServer.setSessionIdManager(idMgr);

      JDBCSessionManager jdbcMgr = new JDBCSessionManager();
      jdbcMgr.setSessionIdManager(uiServer.getSessionIdManager());
      context.setSessionHandler(new SessionHandler(jdbcMgr));

      uiServer.setHandler(context);

      uiServer.start();

      shutdownLock.acquire();

    } catch (Exception e) {
      System.out.println(e);
      throw new RuntimeException(e);
    }
  }

  public static String buildConnectURL(DatabaseConnectionConfiguration config) {

    StringBuilder builder = new StringBuilder("jdbc:")
        .append(config.getAdapter())
        .append("://")
        .append(config.getHost());

    if (config.getPort().isPresent()) {
      builder.append(":").append(config.getPort().get());
    }

    return builder
        .append("/")
        .append(config.getDatabaseName())
        .toString();
  }

  public static void main(String[] args) throws InterruptedException {

    WorkflowDbWebServer server = new WorkflowDbWebServer();
    Thread thread1 = new Thread(server);

    thread1.start();
    thread1.join();
  }
}