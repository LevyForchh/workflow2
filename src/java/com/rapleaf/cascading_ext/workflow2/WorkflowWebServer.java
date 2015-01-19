package com.rapleaf.cascading_ext.workflow2;

import java.net.URL;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import com.liveramp.hank.util.Condition;
import com.liveramp.hank.util.WaitUntil;
import com.rapleaf.cascading_ext.workflow2.state.WorkflowStatePersistence;
import com.rapleaf.support.collections.Accessors;

public class WorkflowWebServer {
  private static final Logger LOG = Logger.getLogger(WorkflowWebServer.class);

  private final int port;
  private final WorkflowRunner workflowRunner;
  private final WorkflowStatePersistence persistence;

  private Server server;

  private WebServerThread webServerThread;

  /**
   * Thread to allow the Jetty status server to run in the background.
   */
  private static class WebServerThread extends Thread {
    private final Server _server;

    public WebServerThread(Server server) {
      super("HadoopWorkflow Web Server Thread");
      setDaemon(true);
      _server = server;
    }

    public void run() {
      try {
        _server.start();
      } catch (Exception e) {
        LOG.error("Got an unexpected exception from server.start()", e);
      }
    }
  }

  public WorkflowWebServer(WorkflowRunner workflowRunner, WorkflowStatePersistence persistence, int port) {
    this.workflowRunner = workflowRunner;
    this.persistence = persistence;
    this.port = port;
  }

  @SuppressWarnings("PMD.JUnit4TestShouldUseTestAnnotation")
  public void start() {
    server = new Server(port);
    final URL warUrl = getClass().getClassLoader().getResource(
      "com/rapleaf/cascading_ext/workflow2/www");

    LOG.info("Workflow WebServer war url: " + warUrl);
    final String warUrlString = warUrl.toExternalForm();
    LOG.info("War url external form: " + warUrlString);

    WebAppContext webAppContext = new WebAppContext(warUrlString, "/");
    webAppContext.setAttribute("workflowRunner", workflowRunner);

    WorkflowDiagram diagram = new WorkflowDiagram(workflowRunner, persistence);

    webAppContext.addServlet(new ServletHolder(new WorkflowStateServlet(diagram)), "/state");
    webAppContext.addServlet(new ServletHolder(new WorkflowCommandServlet(workflowRunner)), "/command");

    server.setHandler(webAppContext);

    webServerThread = new WebServerThread(server);

    webServerThread.start();

    try {
      WaitUntil.orDie(new Condition() {
        @Override
        public boolean test() {
          return getBoundPort() != -1;
        }
      });
      
    }catch(Exception e){
      LOG.info("Failed to bind UI server to a real port!", e);
    }
  }

  public int getBoundPort(){
    Optional<Connector> connector = Accessors.firstOrAbsent(Lists.newArrayList(server.getConnectors()));
    if (connector.isPresent()) {
      return connector.get().getLocalPort();
    } else {
      return -1;
    }
  }

  public void stop() {
    try {
      server.stop();
      webServerThread.join();
    } catch (Exception e) {
      LOG.error("Exception stopping web server!", e);
    }
  }
}
