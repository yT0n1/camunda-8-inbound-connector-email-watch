package io.camunda.connector.inbound;


/**
 * Configuration properties for inbound Connector
 */
public class MyConnectorProperties {

  private String username;
  private String password;
  private String url;
  private String port;
  private String timeout;
  private String folder;
  private String pollingInterval;
  private String gcsBucketName;
  private String gcsProject;
  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getGcsBucketName() {
    return gcsBucketName;
  }

  public void setGcsBucketName(String gcsBucketName) {
    this.gcsBucketName = gcsBucketName;
  }

  public String getGcsProject() {
    return gcsProject;
  }

  public void setGcsProject(String gcsProject) {
    this.gcsProject = gcsProject;
  }

  public String getUsername() {
    return username;
  }
  public String getPassword() {
    return password;
  }
  public String getUrl() {
    return url;
  }
  public String getPort() {
    return port;
  }
  public String getTimeout() {
    return timeout;
  }
  public String getFolder() {
    return folder;
  }
  public String getPollingInterval() {
    return pollingInterval;
  }

  public void setUsername(String username) {
    this.username = username;
  }
  public void setPassword(String password) {
    this.password = password;
  }
  public void setUrl(String url) {
    this.url = url;
  }
  public void setPort(String port) {
    this.port = port;
  }
  public void setTimeout(String timeout) {
    this.timeout = timeout;
  }
  public void setFolder(String folder) {
    this.folder = folder;
  }
  public void setPollingInterval(String pollingInterval) {
    this.pollingInterval = pollingInterval;
  }

  @Override
  public String toString() {
    return "MyConnectorProperties{" + "username='" + username + '\'' + ", password='" + password + '\'' + ", url='"
        + url + '\'' + ", port='" + port + '\'' + ", timeout='" + timeout + '\'' + ", folder='" + folder + '\''
        + ", pollingInterval='" + pollingInterval + '\'' + ", gcsBucketName='" + gcsBucketName + '\'' + ", gcsProject='"
        + gcsProject + '\'' + '}';
  }
}
