package de.peterspace.cardanodbsyncapi.config;

import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@WebFilter
public class RequestLoggingFilterConfig extends CommonsRequestLoggingFilter {

  public RequestLoggingFilterConfig() {
    super();
    setAfterMessagePrefix("");
    setAfterMessageSuffix("");
    setIncludeQueryString(true);
    setIncludePayload(true);
    setMaxPayloadLength(10000);
    setIncludeHeaders(false);
    setIncludeClientInfo(false);
  }

  private long startTime;

  @Override
  protected void beforeRequest(HttpServletRequest request, String message) {
    this.startTime = System.currentTimeMillis();
  }

  @Override
  protected void afterRequest(HttpServletRequest request, String message) {
    long endtime = System.currentTimeMillis();
    long elapsedTime = endtime - startTime;
    super.afterRequest(request, message + " took " + elapsedTime + "ms");
  }
}
