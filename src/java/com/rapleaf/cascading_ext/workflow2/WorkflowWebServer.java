package com.rapleaf.cascading_ext.workflow2;

import java.net.URL;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

public class WorkflowWebServer {
  private static final Logger LOG = Logger.getLogger(WorkflowWebServer.class);

  private final int port;
  private final WorkflowRunner workflowRunner;

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

  public WorkflowWebServer(WorkflowRunner workflowRunner, int port) {
    this.workflowRunner = workflowRunner;
    this.port = port;
  }

  public void start() {
    server = new Server(port);
    final URL warUrl = getClass().getClassLoader().getResource(
      "com/rapleaf/cascading_ext/workflow2/webui");

    LOG.info("Workflow WebServer war url: " + warUrl);
    final String warUrlString = warUrl.toExternalForm();
    LOG.info("War url external form: " + warUrlString);

    WebAppContext webAppContext = new WebAppContext(warUrlString, "/");
    webAppContext.setAttribute("workflowRunner", workflowRunner);
    webAppContext.addServlet(new ServletHolder(new WorkflowStateServlet(new WorkflowDiagram(workflowRunner))), "/state");

    server.setHandler(webAppContext);

    webServerThread = new WebServerThread(server);

    webServerThread.start();
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
